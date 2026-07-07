package com.example.feat1.DDD.inventory_context.application;

import com.example.feat1.DDD.inventory_context.domain.model.InventoryDomainException;
import com.example.feat1.DDD.inventory_context.domain.port.InventoryStockResultPublisher;
import com.example.feat1.DDD.inventory_context.domain.port.MenuRecipeCostingPort;
import com.example.feat1.DDD.inventory_context.domain.service.UnitConverter;
import com.example.feat1.DDD.inventory_context.domain.snapshot.RecipeCostingSnapshot;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.IngredientEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryProcessedEventEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryStockBalanceEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.StockReservationEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.IngredientRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryProcessedEventRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryStockBalanceRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.StockReservationRepository;
import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import com.example.feat1.DDD.order_context.application.event.OrderCreatedEvent;
import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent;
import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent.Result;
import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent.Shortfall;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Core reservation saga handler for the order-confirmation flow (D-02/D-03/D-06/D-09/D-10/D-11).
 *
 * <p>On an {@link OrderCreatedEvent} it: (1) guards idempotency via the inventory processed-events
 * ledger + the unique per-order reservation; (2) resolves per-ingredient required quantities by
 * reusing the shared costing recipe-resolution path with base-unit conversion; (3) checks {@code
 * available = onHand - reserved} on {@code PESSIMISTIC_WRITE}-locked rows acquired in canonical
 * sorted-ingredientId order (deadlock avoidance); (4) reserves atomically only when every
 * ingredient is sufficient, otherwise rejects with shortfalls; and (5) publishes the {@link
 * OrderStockResultEvent} only after the transaction commits.
 *
 * <p>The Kafka listener (15-04) is a thin delegate to {@link #onOrderCreated(OrderCreatedEvent)};
 * all business logic lives here.
 */
@Service
@RequiredArgsConstructor
public class InventoryReservationService {
  private static final Logger log = LoggerFactory.getLogger(InventoryReservationService.class);

  /** Consumer name recorded in the idempotency ledger for this handler. */
  public static final String CONSUMER_NAME = "inventory-order-created";

  private static final int QUANTITY_SCALE = 6;
  private static final String DEFAULT_LOCATION = InventoryStockBalanceEntity.DEFAULT_LOCATION;

  private final InventoryProcessedEventRepository processedEventRepository;
  private final StockReservationRepository reservationRepository;
  private final InventoryStockBalanceRepository balanceRepository;
  private final IngredientRepository ingredientRepository;
  private final MenuRecipeCostingPort menuRecipeCostingPort;
  private final InventoryStockResultPublisher stockResultPublisher;

  @Transactional
  public void onOrderCreated(OrderCreatedEvent event) {
    UUID eventId = event.eventId();
    UUID orderId = event.orderId();

    // (1) Idempotency guard: fast pre-check, then insert+flush the ledger row and rely on the
    // unique (event_id, consumer_name) constraint to catch a concurrent duplicate (D-03).
    if (processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)
        || reservationRepository.existsByOrderId(orderId)) {
      log.debug("Skipping already-processed OrderCreated eventId={} orderId={}", eventId, orderId);
      return;
    }
    try {
      InventoryProcessedEventEntity ledger = new InventoryProcessedEventEntity();
      ledger.setEventId(eventId);
      ledger.setConsumerName(CONSUMER_NAME);
      ledger.setProcessedAt(Instant.now());
      // saveAndFlush forces the INSERT now (the @Id is GenerationType.UUID and would otherwise not
      // flush until commit), so a concurrent-duplicate violation surfaces inside this try block.
      processedEventRepository.saveAndFlush(ledger);
    } catch (DataIntegrityViolationException duplicate) {
      log.debug(
          "Concurrent duplicate OrderCreated eventId={} orderId={} — skipping", eventId, orderId);
      return;
    }

    // (2) Resolve per-ingredient required quantities via the shared costing recipe path (D-06).
    Map<UUID, BigDecimal> required = computeRequired(event);

    // (3) Canonical iteration order (ascending UUID) used for BOTH the availability check and the
    // reserve loop so concurrent reservers acquire row locks in the same sequence (D-11 / T-15-11).
    List<UUID> sortedIngredientIds = required.keySet().stream().sorted().toList();

    Map<UUID, InventoryStockBalanceEntity> lockedBalances = new LinkedHashMap<>();
    List<Shortfall> shortfalls = new ArrayList<>();
    for (UUID ingredientId : sortedIngredientIds) {
      BigDecimal need = required.get(ingredientId);
      Optional<InventoryStockBalanceEntity> balance =
          balanceRepository.lockByIngredientAndLocation(ingredientId, DEFAULT_LOCATION);
      if (balance.isEmpty()) {
        shortfalls.add(
            new Shortfall(ingredientId, ingredientName(ingredientId, null), need, BigDecimal.ZERO));
        continue;
      }
      InventoryStockBalanceEntity entity = balance.get();
      lockedBalances.put(ingredientId, entity);
      BigDecimal available = entity.getQuantityOnHand().subtract(entity.getReservedQuantity());
      if (available.compareTo(need) < 0) {
        shortfalls.add(
            new Shortfall(ingredientId, ingredientName(ingredientId, entity), need, available));
      }
    }

    OrderStockResultEvent result;
    if (shortfalls.isEmpty()) {
      // (4) Reserve on the SAME sorted list: increment reserved (available stays >= 0, D-02) and
      // record the per-order reservation for Phase-16 settlement (D-09).
      for (UUID ingredientId : sortedIngredientIds) {
        InventoryStockBalanceEntity entity = lockedBalances.get(ingredientId);
        entity.setReservedQuantity(
            scale(entity.getReservedQuantity().add(required.get(ingredientId))));
        balanceRepository.save(entity);
      }
      reservationRepository.save(StockReservationEntity.held(orderId, required));
      result =
          new OrderStockResultEvent(
              UUID.randomUUID(),
              OrderStockResultEvent.CONFIRMED_TYPE,
              Instant.now(),
              orderId,
              Result.CONFIRMED,
              List.of());
    } else {
      result =
          new OrderStockResultEvent(
              UUID.randomUUID(),
              OrderStockResultEvent.REJECTED_TYPE,
              Instant.now(),
              orderId,
              Result.REJECTED,
              List.copyOf(shortfalls));
    }

    // (5) Publish only after the reservation transaction commits (D-10 / T-15-04).
    publishAfterCommit(result);
  }

  /**
   * Resolves the total required base quantity per ingredient for the order by summing, for every
   * line, its DISH recipe and each selected topping's TOPPING_OPTION recipe (each recipe-line
   * quantity converted to the ingredient base unit and multiplied by the order line quantity). A
   * missing recipe, null ingredient link, unknown ingredient, or unconvertible unit contributes
   * ZERO and never throws or blocks confirmation (D-06 / T-15-06).
   */
  private Map<UUID, BigDecimal> computeRequired(OrderCreatedEvent event) {
    Map<UUID, BigDecimal> required = new LinkedHashMap<>();
    if (event.lines() == null) {
      return required;
    }
    for (OrderCreatedEvent.OrderLine line : event.lines()) {
      int quantity = line.quantity();
      accumulateRecipe(required, RecipeTargetType.DISH, line.dishId(), quantity);
      if (line.selectedToppings() != null) {
        for (OrderCreatedEvent.OrderTopping topping : line.selectedToppings()) {
          accumulateRecipe(
              required, RecipeTargetType.TOPPING_OPTION, topping.toppingOptionId(), quantity);
        }
      }
    }
    return required;
  }

  private void accumulateRecipe(
      Map<UUID, BigDecimal> required,
      RecipeTargetType targetType,
      UUID targetId,
      int orderLineQuantity) {
    if (targetId == null) {
      return;
    }
    Optional<RecipeCostingSnapshot> recipe = menuRecipeCostingPort.findRecipe(targetType, targetId);
    if (recipe.isEmpty()) {
      log.debug("No recipe for {} {} — contributes zero required stock", targetType, targetId);
      return;
    }
    for (RecipeCostingSnapshot.Line line : recipe.get().lines()) {
      UUID ingredientId = line.ingredientId();
      if (ingredientId == null) {
        continue;
      }
      Optional<IngredientEntity> ingredient = ingredientRepository.findById(ingredientId);
      if (ingredient.isEmpty()) {
        log.debug("Ingredient {} not found — contributes zero required stock", ingredientId);
        continue;
      }
      String baseUnit = ingredient.get().getBaseUnit();
      BigDecimal converted;
      try {
        converted = UnitConverter.convert(line.quantity(), line.unit(), baseUnit);
      } catch (InventoryDomainException unconvertible) {
        log.debug(
            "Cannot convert {} {} to base unit {} for ingredient {} — contributes zero",
            line.quantity(),
            line.unit(),
            baseUnit,
            ingredientId);
        continue;
      }
      BigDecimal contribution = converted.multiply(BigDecimal.valueOf(orderLineQuantity));
      required.merge(ingredientId, contribution, BigDecimal::add);
    }
  }

  private String ingredientName(UUID ingredientId, InventoryStockBalanceEntity balance) {
    if (balance != null && balance.getIngredient() != null) {
      return balance.getIngredient().getName();
    }
    return ingredientRepository.findById(ingredientId).map(IngredientEntity::getName).orElse(null);
  }

  private void publishAfterCommit(OrderStockResultEvent event) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      stockResultPublisher.publishStockResult(event);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            stockResultPublisher.publishStockResult(event);
          }
        });
  }

  private BigDecimal scale(BigDecimal value) {
    return value.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
  }
}
