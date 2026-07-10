package com.example.feat1.DDD.inventory_context.application;

import com.example.feat1.DDD.inventory_context.domain.model.InventoryDomainException;
import com.example.feat1.DDD.inventory_context.domain.model.InventoryMovementType;
import com.example.feat1.DDD.inventory_context.domain.port.OrderLineLookupPort;
import com.example.feat1.DDD.inventory_context.domain.service.RecipeRequirementResolver;
import com.example.feat1.DDD.inventory_context.domain.snapshot.OrderLineRecipeSnapshot;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.IngredientEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryLineReleaseEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryProcessedEventEntity;
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
import com.example.feat1.DDD.order_context.application.event.OrderCancelledEvent;
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
 * Releases held reservations for cancelled order lines (CANCEL-05). This is the structural inverse
 * of {@link InventoryReservationSettlementService}: instead of decrementing BOTH {@code
 * reservedQuantity} and {@code quantityOnHand}, it decrements {@code reservedQuantity} ONLY,
 * leaving {@code quantityOnHand} untouched, and writes a {@code RESERVATION_RELEASE} audit movement
 * instead of {@code CONSUMPTION}.
 *
 * <p>On an {@link OrderCancelledEvent} it: (1) short-circuits on either idempotency guard — the
 * eventId ledger or every cancelled line already carrying a release row (T-18-03-01); (2)
 * re-resolves each cancelled line's recipe via the shared {@link RecipeRequirementResolver} (never
 * reads {@link StockReservationEntity#getLines()}); (3) locks the reservation row FIRST, then
 * ingredient balance rows in ascending-ingredientId order (deadlock-free total order, mirrors
 * T-18-03-02); (4) subtracts {@code reservedQuantity} with a non-negative clamp that logs an
 * anomaly and never throws; (5) writes a {@code RESERVATION_RELEASE} audit movement per ingredient
 * with a null (system) actor (T-18-03-04); (6) records a per-line release row, skipping any
 * individual line already released under a prior redelivery; and (7) flips the reservation to
 * {@code RELEASED} once {@code settledCount + releasedCount >= totalLines} — the same widened
 * denominator the settlement service's own completion guard uses, so a mixed settled/released order
 * still reaches a terminal state.
 *
 * <p>Uses the ledger-insert-LAST-in-same-transaction idiom (not the isolated {@link
 * InventoryLedgerWriter} REQUIRES_NEW writer, which is reserved for settlement's specific
 * non-transactional side-effect risk): release is a pure same-database JPA write with nothing
 * outside the transaction to protect.
 *
 * <p>A missing reservation throws (routed to retry then DLT by the listener wiring) rather than
 * being silently swallowed, so a transient cancel-before-reserve ordering race can self-heal.
 */
@Service
@RequiredArgsConstructor
public class InventoryReservationReleaseService {
  private static final Logger log =
      LoggerFactory.getLogger(InventoryReservationReleaseService.class);

  /** Consumer name recorded in the idempotency ledger for this handler. */
  public static final String CONSUMER_NAME = "inventory-release";

  private static final int QUANTITY_SCALE = 6;
  private static final String DEFAULT_LOCATION = InventoryStockBalanceEntity.DEFAULT_LOCATION;
  private static final String REFERENCE_TYPE = "ORDER_LINE";

  private final InventoryProcessedEventRepository processedEventRepository;
  private final InventoryLineReleaseRepository lineReleaseRepository;
  private final InventoryLineSettlementRepository lineSettlementRepository;
  private final StockReservationRepository reservationRepository;
  private final InventoryStockBalanceRepository balanceRepository;
  private final InventoryStockMovementRepository movementRepository;
  private final OrderLineLookupPort orderLineLookupPort;
  private final RecipeRequirementResolver recipeRequirementResolver;

  @Transactional
  public void onOrderCancelled(OrderCancelledEvent event) {
    UUID eventId = event.eventId();
    UUID orderId = event.orderId();
    List<UUID> cancelledLineIds = event.cancelledLineIds();

    // (1) Two independent idempotency guards. The eventId ledger catches a replayed delivery of
    // this exact event; "every cancelled line already released" catches the same lines arriving
    // under a NEW eventId (T-18-03-01). Neither is redundant.
    boolean allLinesAlreadyReleased =
        !cancelledLineIds.isEmpty()
            && cancelledLineIds.stream()
                .allMatch(
                    lineId -> lineReleaseRepository.existsByOrderIdAndOrderLineId(orderId, lineId));
    if (processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)
        || allLinesAlreadyReleased) {
      log.debug("Skipping already-released OrderCancelled eventId={} orderId={}", eventId, orderId);
      return;
    }

    // (2) Lock the reservation row FIRST (canonical total lock order — reservation before
    // balances, mirrors settlement). A missing reservation is uncaught so it retries then lands
    // on the DLT.
    StockReservationEntity reservation =
        reservationRepository
            .lockByOrderId(orderId)
            .orElseThrow(() -> InventoryDomainException.releaseReservationMissing(orderId));
    if (reservation.getStatus() != ReservationStatus.HELD) {
      // Already fully resolved (settled or released) — benign redelivery signal, not an error.
      log.debug("Reservation for order {} already resolved — skipping release", orderId);
      return;
    }

    Instant now = Instant.now();
    for (UUID orderLineId : cancelledLineIds) {
      if (lineReleaseRepository.existsByOrderIdAndOrderLineId(orderId, orderLineId)) {
        // This individual line was already released under a prior (possibly overlapping)
        // delivery — skip it without double-decrementing.
        log.debug("Order line {} for order {} already released — skipping", orderLineId, orderId);
        continue;
      }

      // (3) Re-resolve THIS line's recipe via the shared resolver — never read the aggregated
      // reservation lines. Missing line data cannot be released — throw so the delivery is
      // retried then routed to the DLT.
      OrderLineRecipeSnapshot line =
          orderLineLookupPort
              .findLine(orderId, orderLineId)
              .orElseThrow(
                  () -> InventoryDomainException.releaseOrderLineMissing(orderId, orderLineId));
      Map<UUID, BigDecimal> required = resolveLineRequirements(line);

      // (4)+(5) Iterate ingredients in ascending-id order so all releases acquire balance locks
      // in the same sequence as settlement (deadlock-free). Subtract reservedQuantity ONLY with a
      // non-negative clamp, then write a RESERVATION_RELEASE audit movement directly.
      List<UUID> sortedIngredientIds = required.keySet().stream().sorted().toList();
      for (UUID ingredientId : sortedIngredientIds) {
        BigDecimal need = required.get(ingredientId);
        Optional<InventoryStockBalanceEntity> locked =
            balanceRepository.lockByIngredientAndLocation(ingredientId, DEFAULT_LOCATION);
        if (locked.isEmpty()) {
          // No balance to release against — cannot release this ingredient; log and continue
          // (never throw).
          log.warn(
              "No stock balance for ingredient {} (order {} line {}) — skipping release",
              ingredientId,
              orderId,
              orderLineId);
          continue;
        }
        InventoryStockBalanceEntity balance = locked.get();

        BigDecimal newReserved = balance.getReservedQuantity().subtract(need);
        if (newReserved.compareTo(BigDecimal.ZERO) < 0) {
          log.warn(
              "Reserved quantity would go negative for ingredient {} (order {}); clamping to 0",
              ingredientId,
              orderId);
          newReserved = BigDecimal.ZERO;
        }
        balance.setReservedQuantity(scale(newReserved));
        // Deliberately never call setQuantityOnHand — release only reverses the hold, it never
        // touches actual stock.
        balance.setLastMovementAt(now);
        balance.setUpdatedAt(now);
        // Managed entity — rely on dirty checking (mirrors the settlement loop; no redundant
        // save).

        IngredientEntity ingredient = balance.getIngredient();
        String baseUnit = ingredient.getBaseUnit();
        InventoryStockMovementEntity movement = new InventoryStockMovementEntity();
        movement.setIngredient(ingredient);
        movement.setLocationCode(DEFAULT_LOCATION);
        movement.setMovementType(InventoryMovementType.RESERVATION_RELEASE);
        movement.setQuantity(scale(need));
        movement.setUnit(baseUnit);
        movement.setBaseQuantityDelta(scale(need.negate()));
        movement.setBaseUnit(baseUnit);
        movement.setResultingBalance(balance.getQuantityOnHand());
        movement.setReferenceType(REFERENCE_TYPE);
        movement.setReferenceId(orderLineId);
        movement.setActorId(null); // system-triggered release, no human actor
        movement.setCreatedAt(now);
        movementRepository.save(movement);
      }

      // (6) Record the per-line release (the durable T-18-03-01 guard + completion counter).
      InventoryLineReleaseEntity release = new InventoryLineReleaseEntity();
      release.setOrderId(orderId);
      release.setOrderLineId(orderLineId);
      release.setReleasedAt(now);
      lineReleaseRepository.save(release);
    }

    // (7) Flip the reservation to RELEASED only when the last line resolves. The denominator
    // counts BOTH settled and released lines — the same widened guard the settlement service
    // uses — so count-then-flip is atomic against sibling settlements/releases because we hold
    // the reservation lock.
    long settledCount = lineSettlementRepository.countByOrderId(orderId);
    long releasedCount = lineReleaseRepository.countByOrderId(orderId);
    if (settledCount + releasedCount >= event.totalLines()) {
      reservation.setStatus(ReservationStatus.RELEASED);
    }

    // (8) Ledger row inserted LAST, in the same transaction, so it commits atomically with the
    // status transition and every release row above.
    recordProcessed(eventId);
  }

  private void recordProcessed(UUID eventId) {
    InventoryProcessedEventEntity ledger = new InventoryProcessedEventEntity();
    ledger.setEventId(eventId);
    ledger.setConsumerName(CONSUMER_NAME);
    ledger.setProcessedAt(Instant.now());
    processedEventRepository.save(ledger);
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
