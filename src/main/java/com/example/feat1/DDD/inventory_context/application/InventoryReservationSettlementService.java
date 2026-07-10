package com.example.feat1.DDD.inventory_context.application;

import com.example.feat1.DDD.inventory_context.application.event.SettleTriggerEvent;
import com.example.feat1.DDD.inventory_context.domain.model.InventoryDomainException;
import com.example.feat1.DDD.inventory_context.domain.model.InventoryMovementType;
import com.example.feat1.DDD.inventory_context.domain.port.OrderLineLookupPort;
import com.example.feat1.DDD.inventory_context.domain.service.RecipeRequirementResolver;
import com.example.feat1.DDD.inventory_context.domain.snapshot.OrderLineRecipeSnapshot;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.IngredientEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryLineSettlementEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryStockBalanceEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryStockMovementEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.StockReservationEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.StockReservationEntity.ReservationStatus;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryLineReleaseRepository;
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
 * incrementing {@code reservedQuantity} it decrements BOTH {@code reservedQuantity} and {@code
 * quantityOnHand} for the one line's re-resolved recipe.
 *
 * <p>The completion guard (step 8) counts BOTH settled AND released lines ({@code settledCount +
 * releasedCount >= totalLines}, phase 18 CANCEL-05) so a mixed settled/released order (some lines
 * consumed at kitchen time, others cancelled first) still reaches a terminal reservation state
 * instead of leaking a permanently-HELD reservation.
 *
 * <p>On a {@link SettleTriggerEvent} it: (1) short-circuits on either idempotency guard — the
 * eventId ledger or the per-(orderId,orderLineId) settlement row (D-05); (2) records the eventId in
 * the {@link InventoryLedgerWriter} REQUIRES_NEW transaction so a concurrent duplicate cannot
 * poison this transaction (WR-01 / D-06); (3) re-resolves the line's recipe via the shared {@link
 * RecipeRequirementResolver} (D-02); (4) locks the reservation row FIRST, then ingredient balance
 * rows in ascending-ingredientId order (deadlock-free total order); (5) subtracts on-hand +
 * reserved with a non-negative clamp that logs an anomaly and never throws (D-03); (6) writes a
 * CONSUMPTION audit movement directly per ingredient (WR-02 / D-06); (7) records the per-line
 * settlement; and (8) flips the reservation to SETTLED only once {@code countByOrderId ==
 * totalLines} (D-04).
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

  private final InventoryLedgerWriter ledgerWriter;
  private final InventoryProcessedEventRepository processedEventRepository;
  private final InventoryLineSettlementRepository lineSettlementRepository;
  private final InventoryLineReleaseRepository lineReleaseRepository;
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

    // (1) Two independent idempotency guards — both must pass. The eventId ledger catches a
    // replayed
    // delivery; the per-(orderId,orderLineId) row catches the same line arriving under a NEW
    // eventId
    // (Pitfall 4). Neither is redundant (D-05).
    if (processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)
        || lineSettlementRepository.existsByOrderIdAndOrderLineId(orderId, orderLineId)) {
      log.debug(
          "Skipping already-settled trigger eventId={} orderId={} orderLineId={}",
          eventId,
          orderId,
          orderLineId);
      return;
    }

    // (2) Isolated REQUIRES_NEW ledger insert. A concurrent duplicate returns false without
    // throwing
    // so this business transaction is never marked rollback-only (WR-01 / D-06).
    if (!ledgerWriter.tryInsert(eventId, CONSUMER_NAME)) {
      log.debug("Concurrent duplicate settle trigger eventId={} — skipping", eventId);
      return;
    }

    // (3) Re-resolve THIS line's recipe via the shared resolver (D-02). Missing line data cannot be
    // settled — throw so the delivery is retried then routed to the DLT (D-05).
    OrderLineRecipeSnapshot line =
        orderLineLookupPort
            .findLine(orderId, orderLineId)
            .orElseThrow(
                () -> InventoryDomainException.settlementOrderLineMissing(orderId, orderLineId));
    Map<UUID, BigDecimal> required = resolveLineRequirements(line);

    // (4) Lock the reservation row FIRST (canonical total lock order — reservation before
    // balances).
    // A missing reservation is uncaught so it retries then lands on the DLT (D-05).
    StockReservationEntity reservation =
        reservationRepository
            .lockByOrderId(orderId)
            .orElseThrow(() -> InventoryDomainException.settlementReservationMissing(orderId));
    if (reservation.getStatus() != ReservationStatus.HELD) {
      // Already fully settled — benign redelivery signal, not an error (Open Q1).
      log.debug(
          "Reservation for order {} already settled — skipping line {}", orderId, orderLineId);
      return;
    }

    // (5)+(6) Iterate ingredients in ascending-id order so all settlements acquire balance locks in
    // the same sequence (deadlock-free). Subtract on-hand + reserved with a non-negative clamp,
    // then
    // write a CONSUMPTION audit movement directly.
    Instant now = Instant.now();
    List<UUID> sortedIngredientIds = required.keySet().stream().sorted().toList();
    for (UUID ingredientId : sortedIngredientIds) {
      BigDecimal need = required.get(ingredientId);
      Optional<InventoryStockBalanceEntity> locked =
          balanceRepository.lockByIngredientAndLocation(ingredientId, DEFAULT_LOCATION);
      if (locked.isEmpty()) {
        // No balance to deduct from — cannot settle this ingredient; log and continue (never
        // throw).
        log.warn(
            "No stock balance for ingredient {} (order {} line {}) — skipping deduction",
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
      // Managed entity — rely on dirty checking (mirrors the reserve loop; no redundant save).

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
      movement.setActorId(null); // system-triggered settlement, no human actor
      movement.setCreatedAt(now);
      movementRepository.save(movement);
    }

    // (7) Record the per-line settlement (the durable D-05 guard + D-04 counter).
    InventoryLineSettlementEntity settlement = new InventoryLineSettlementEntity();
    settlement.setOrderId(orderId);
    settlement.setOrderLineId(orderLineId);
    settlement.setSettledAt(now);
    lineSettlementRepository.save(settlement);

    // (8) Flip the reservation to SETTLED only when the last line resolves — the denominator
    // counts BOTH settled and released lines so a mixed settled/released order still reaches a
    // terminal state (phase 18 CANCEL-05). count-then-flip is atomic against sibling
    // settlements/releases because we hold the reservation lock (D-04).
    long settledCount = lineSettlementRepository.countByOrderId(orderId);
    long releasedCount = lineReleaseRepository.countByOrderId(orderId);
    if (settledCount + releasedCount >= event.totalLines()) {
      reservation.setStatus(ReservationStatus.SETTLED);
    }
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
}
