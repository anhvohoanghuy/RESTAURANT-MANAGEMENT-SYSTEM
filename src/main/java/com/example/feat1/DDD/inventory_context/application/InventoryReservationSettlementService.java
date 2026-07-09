package com.example.feat1.DDD.inventory_context.application;

import com.example.feat1.DDD.inventory_context.application.event.SettleTriggerEvent;
import com.example.feat1.DDD.inventory_context.domain.model.InventoryDomainException;
import com.example.feat1.DDD.inventory_context.domain.model.InventoryMovementType;
import com.example.feat1.DDD.inventory_context.domain.port.OrderLineLookupPort;
import com.example.feat1.DDD.inventory_context.domain.service.RecipeRequirementResolver;
import com.example.feat1.DDD.inventory_context.domain.snapshot.OrderLineRecipeSnapshot;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.IngredientEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryLineSettlementEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryProcessedEventEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryStockBalanceEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryStockMovementEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.StockReservationEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.StockReservationEntity.ReservationStatus;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryLineSettlementRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryProcessedEventRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryStockBalanceRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryStockMovementRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.StockReservationRepository;
import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settles a single order line's held reservation into an actual stock deduction (D-02..D-06). This
 * is the inverse of the whole-order {@link InventoryReservationService} reserve saga: instead of
 * incrementing {@code reservedQuantity} it decrements both {@code reservedQuantity} and {@code
 * quantityOnHand} for the one line's re-resolved recipe.
 *
 * <p>On a {@link SettleTriggerEvent} it: (1) short-circuits on either idempotency guard, the
 * eventId ledger or the per-(orderId, orderLineId) settlement row (D-05); (2) re-resolves the
 * line's recipe via the shared {@link RecipeRequirementResolver} (D-02); (3) locks the reservation
 * row first, then ingredient balance rows in ascending ingredientId order; (4) subtracts on-hand
 * and reserved with a non-negative clamp (D-03); (5) writes a CONSUMPTION audit movement directly
 * per ingredient (WR-02 / D-06); (6) records the per-line settlement; (7) flips the reservation to
 * SETTLED only once {@code countByOrderId == totalLines} (D-04); and (8) records the
 * processed-event ledger row last, in the same transaction as the settlement mutation.
 *
 * <p>A missing reservation throws (routed to retry then DLT by the listener wiring) rather than
 * being silently swallowed, so a transient settle-before-reserve ordering race can self-heal
 * (D-05).
 */
@Service
@RequiredArgsConstructor
public class InventoryReservationSettlementService {
  private static final Logger log =
      LoggerFactory.getLogger(InventoryReservationSettlementService.class);

  /** Consumer name recorded in the idempotency ledger for this handler. */
  public static final String CONSUMER_NAME = "inventory-settlement";

  private static final int QUANTITY_SCALE = 6;
  private static final String DEFAULT_LOCATION = InventoryStockBalanceEntity.DEFAULT_LOCATION;
  private static final String REFERENCE_TYPE = "ORDER_LINE";

  private final InventoryProcessedEventRepository processedEventRepository;
  private final InventoryLineSettlementRepository lineSettlementRepository;
  private final StockReservationRepository reservationRepository;
  private final InventoryStockBalanceRepository balanceRepository;
  private final InventoryStockMovementRepository movementRepository;
  private final OrderLineLookupPort orderLineLookupPort;
  private final RecipeRequirementResolver recipeRequirementResolver;

  @Transactional
  public void onSettleTrigger(SettleTriggerEvent event) {
    UUID eventId = event.eventId();
    UUID orderId = event.orderId();
    UUID orderLineId = event.orderLineId();

    // Two independent idempotency guards: eventId replay and same order-line under a new eventId.
    if (processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)
        || lineSettlementRepository.existsByOrderIdAndOrderLineId(orderId, orderLineId)) {
      log.debug(
          "Skipping already-settled trigger eventId={} orderId={} orderLineId={}",
          eventId,
          orderId,
          orderLineId);
      return;
    }

    OrderLineRecipeSnapshot line =
        orderLineLookupPort
            .findLine(orderId, orderLineId)
            .orElseThrow(
                () -> InventoryDomainException.settlementOrderLineMissing(orderId, orderLineId));
    Map<UUID, BigDecimal> required = resolveLineRequirements(line);

    StockReservationEntity reservation =
        reservationRepository
            .lockByOrderId(orderId)
            .orElseThrow(() -> InventoryDomainException.settlementReservationMissing(orderId));
    if (reservation.getStatus() != ReservationStatus.HELD) {
      log.debug(
          "Reservation for order {} already settled - skipping line {}", orderId, orderLineId);
      return;
    }

    Instant now = Instant.now();
    List<UUID> sortedIngredientIds = required.keySet().stream().sorted().toList();
    for (UUID ingredientId : sortedIngredientIds) {
      BigDecimal need = required.get(ingredientId);
      Optional<InventoryStockBalanceEntity> locked =
          balanceRepository.lockByIngredientAndLocation(ingredientId, DEFAULT_LOCATION);
      if (locked.isEmpty()) {
        log.warn(
            "No stock balance for ingredient {} (order {} line {}) - skipping deduction",
            ingredientId,
            orderId,
            orderLineId);
        continue;
      }

      InventoryStockBalanceEntity balance = locked.get();
      BigDecimal newReserved = balance.getReservedQuantity().subtract(need);
      BigDecimal newOnHand = balance.getQuantityOnHand().subtract(need);
      if (newReserved.compareTo(BigDecimal.ZERO) < 0) {
        log.warn(
            "Reserved quantity would go negative for ingredient {} (order {}); clamping to 0",
            ingredientId,
            orderId);
        newReserved = BigDecimal.ZERO;
      }
      if (newOnHand.compareTo(BigDecimal.ZERO) < 0) {
        log.warn(
            "On-hand would go negative for ingredient {} (order {}); clamping to 0 (D-03)",
            ingredientId,
            orderId);
        newOnHand = BigDecimal.ZERO;
      }

      BigDecimal scaledOnHand = scale(newOnHand);
      balance.setReservedQuantity(scale(newReserved));
      balance.setQuantityOnHand(scaledOnHand);
      balance.setLastMovementAt(now);
      balance.setUpdatedAt(now);

      IngredientEntity ingredient = balance.getIngredient();
      String baseUnit = ingredient.getBaseUnit();
      InventoryStockMovementEntity movement = new InventoryStockMovementEntity();
      movement.setIngredient(ingredient);
      movement.setLocationCode(DEFAULT_LOCATION);
      movement.setMovementType(InventoryMovementType.CONSUMPTION);
      movement.setQuantity(scale(need));
      movement.setUnit(baseUnit);
      movement.setBaseQuantityDelta(scale(need.negate()));
      movement.setBaseUnit(baseUnit);
      movement.setResultingBalance(scaledOnHand);
      movement.setReferenceType(REFERENCE_TYPE);
      movement.setReferenceId(orderLineId);
      movement.setActorId(null);
      movement.setCreatedAt(now);
      movementRepository.save(movement);
    }

    InventoryLineSettlementEntity settlement = new InventoryLineSettlementEntity();
    settlement.setOrderId(orderId);
    settlement.setOrderLineId(orderLineId);
    settlement.setSettledAt(now);
    lineSettlementRepository.save(settlement);

    long settledCount = lineSettlementRepository.countByOrderId(orderId);
    if (settledCount >= event.totalLines()) {
      reservation.setStatus(ReservationStatus.SETTLED);
    }

    recordProcessed(eventId);
  }

  private Map<UUID, BigDecimal> resolveLineRequirements(OrderLineRecipeSnapshot line) {
    int quantity = line.quantity();
    Map<UUID, BigDecimal> required =
        recipeRequirementResolver.resolveForTarget(RecipeTargetType.DISH, line.dishId(), quantity);
    if (line.selectedToppingOptionIds() != null) {
      for (UUID toppingOptionId : line.selectedToppingOptionIds()) {
        recipeRequirementResolver.accumulate(
            required, RecipeTargetType.TOPPING_OPTION, toppingOptionId, quantity);
      }
    }
    return required;
  }

  private BigDecimal scale(BigDecimal value) {
    return value.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
  }

  private void recordProcessed(UUID eventId) {
    InventoryProcessedEventEntity ledger = new InventoryProcessedEventEntity();
    ledger.setEventId(eventId);
    ledger.setConsumerName(CONSUMER_NAME);
    ledger.setProcessedAt(Instant.now());
    processedEventRepository.save(ledger);
  }
}
