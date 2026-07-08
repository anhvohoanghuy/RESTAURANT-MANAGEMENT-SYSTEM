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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    // (1) Idempotency: fast pre-check, then insert + immediate flush as the authoritative guard.
    if (processedEventRepository.existsByEventIdAndConsumerName(event.eventId(), CONSUMER_NAME)) {
      return;
    }
    try {
      OrderProcessedEventEntity ledger = new OrderProcessedEventEntity();
      ledger.setEventId(event.eventId());
      ledger.setConsumerName(CONSUMER_NAME);
      ledger.setProcessedAt(Instant.now());
      processedEventRepository.saveAndFlush(ledger);
    } catch (DataIntegrityViolationException duplicate) {
      // Concurrent delivery inserted the same (eventId, consumer) first -- treat as a replay.
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
    int targetRank = FULFILLMENT_RANK.getOrDefault(target, -1);
    int currentRank = FULFILLMENT_RANK.getOrDefault(order.getStatus(), -1);
    if (targetRank <= currentRank) {
      return;
    }

    order.setStatus(target);
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
