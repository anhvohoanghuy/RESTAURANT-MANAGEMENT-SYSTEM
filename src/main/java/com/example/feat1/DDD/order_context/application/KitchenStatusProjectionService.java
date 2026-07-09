package com.example.feat1.DDD.order_context.application;

import com.example.feat1.DDD.kitchen_context.application.event.KitchenTicketStatusChangedEvent;
import com.example.feat1.DDD.kitchen_context.application.event.KitchenTicketStatusChangedEvent.ItemStatus;
import com.example.feat1.DDD.kitchen_context.domain.model.KitchenItemStatus;
import com.example.feat1.DDD.order_context.domain.model.OrderStatus;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderProcessedEventEntity;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderProcessedEventRepository;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderRepository;
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
 * Consumer half of D-04: reflects kitchen fulfillment onto the order aggregate purely by event.
 * order_context is a pure consumer here -- it never mutates kitchen state, and kitchen never writes
 * {@link OrderEntity} directly.
 *
 * <p>Derives the order's aggregate status from the FULL per-item snapshot carried by {@link
 * KitchenTicketStatusChangedEvent}: the order only advances to a stage once EVERY item has reached
 * it, except PREPARING, which is applied as soon as ANY item has started (the order isn't "ready"
 * while even one item is still preparing).
 *
 * <p>Guards against Kafka redelivery/reordering (T-17-17): a derived status is only applied when it
 * strictly increases {@link #FULFILLMENT_RANK} versus the order's current status, and a REJECTED
 * order (terminal, D-11) is never modified regardless of the incoming snapshot. Idempotency is
 * guarded by the shared {@code order_processed_events} ledger, reusing the same
 * insert+flush-then-catch-unique-violation pattern as {@link OrderConfirmationService} (T-17-19).
 */
@Service
@RequiredArgsConstructor
public class KitchenStatusProjectionService {

  private static final Logger log = LoggerFactory.getLogger(KitchenStatusProjectionService.class);

  /** Ledger consumer identity for the order-side kitchen-status projection. */
  static final String CONSUMER_NAME = "kitchen-status-projection";

  /**
   * Forward-only rank of each fulfillment-relevant order status. Declaration mirrors {@link
   * OrderStatus}'s pinned CONFIRMED..COMPLETED ordering; only strictly increasing transitions are
   * applied (T-17-17).
   */
  private static final Map<OrderStatus, Integer> FULFILLMENT_RANK =
      Map.of(
          OrderStatus.CONFIRMED, 0,
          OrderStatus.PREPARING, 1,
          OrderStatus.READY, 2,
          OrderStatus.SERVED, 3,
          OrderStatus.COMPLETED, 4);

  private final OrderProcessedEventRepository processedEventRepository;
  private final OrderRepository orderRepository;

  @Transactional
  public void onTicketStatusChanged(KitchenTicketStatusChangedEvent event) {
    // (1) Idempotency fast pre-check: absorb an already-recorded replay cheaply. The authoritative
    // ledger insert happens at the END of this method, in this same transaction (I-WR-01 / CR-01
    // fix).
    if (processedEventRepository.existsByEventIdAndConsumerName(event.eventId(), CONSUMER_NAME)) {
      return;
    }

    // (2) Load the order; nothing to project onto if it doesn't exist.
    Optional<OrderEntity> maybeOrder = orderRepository.findById(event.orderId());
    if (maybeOrder.isEmpty()) {
      return;
    }
    OrderEntity order = maybeOrder.get();

    // (3) REJECTED is terminal (D-11) -- kitchen fulfillment progress never resurrects it.
    if (order.getStatus() == OrderStatus.REJECTED) {
      return;
    }

    // (4) Derive the target status from the per-item snapshot.
    OrderStatus target = deriveTargetStatus(event.items());
    if (target == null) {
      return;
    }

    // (5) Rank guard: only apply if the derived status strictly advances the order (T-17-17).
    // Fail-closed (K-WR-03): an unknown CURRENT rank (e.g. order is still
    // PENDING_CONFIRMATION/SUBMITTED, pre-CONFIRMED) must never be overwritten by a fulfillment
    // snapshot -- getOrDefault(-1) on ONLY the target used to fail-open here and let any
    // fulfillment status skip past CONFIRMED.
    int targetRank = FULFILLMENT_RANK.getOrDefault(target, -1);
    int currentRank = FULFILLMENT_RANK.getOrDefault(order.getStatus(), -1);
    if (currentRank < 0) {
      log.warn(
          "Unknown fulfillment rank for order {} status {} -- skipping projection to {}",
          order.getId(),
          order.getStatus(),
          target);
      return;
    }
    if (targetRank <= currentRank) {
      return;
    }

    order.setStatus(target);

    // Record the idempotency-ledger row LAST, in THIS transaction, so it commits atomically with
    // the status advance. A concurrent-duplicate unique violation rolls back the WHOLE transaction
    // (Kafka redelivers; the pre-check + forward-only rank guard then absorb the replay) instead of
    // pre-committing a "processed" marker in a separate REQUIRES_NEW transaction ahead of the
    // projection write (CR-01 / I-WR-01 fix).
    recordProcessed(event.eventId());
  }

  private void recordProcessed(UUID eventId) {
    OrderProcessedEventEntity ledger = new OrderProcessedEventEntity();
    ledger.setEventId(eventId);
    ledger.setConsumerName(CONSUMER_NAME);
    ledger.setProcessedAt(Instant.now());
    processedEventRepository.save(ledger);
  }

  private OrderStatus deriveTargetStatus(List<ItemStatus> items) {
    if (items == null || items.isEmpty()) {
      return null;
    }
    if (items.stream().allMatch(item -> item.status() == KitchenItemStatus.COMPLETED)) {
      return OrderStatus.COMPLETED;
    }
    if (items.stream()
        .allMatch(item -> item.status().ordinal() >= KitchenItemStatus.SERVED.ordinal())) {
      return OrderStatus.SERVED;
    }
    if (items.stream()
        .allMatch(item -> item.status().ordinal() >= KitchenItemStatus.READY.ordinal())) {
      return OrderStatus.READY;
    }
    if (items.stream()
        .anyMatch(item -> item.status().ordinal() >= KitchenItemStatus.PREPARING.ordinal())) {
      return OrderStatus.PREPARING;
    }
    return null;
  }
}
