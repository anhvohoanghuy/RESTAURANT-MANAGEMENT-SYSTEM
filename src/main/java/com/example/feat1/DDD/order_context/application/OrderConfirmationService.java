package com.example.feat1.DDD.order_context.application;

import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent;
import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent.Result;
import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent.Shortfall;
import com.example.feat1.DDD.order_context.domain.model.OrderStatus;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderProcessedEventEntity;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderProcessedEventRepository;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Terminal half of the order-confirmation saga: reacts to Inventory's stock verdict and
 * idempotently transitions the order from {@link OrderStatus#PENDING_CONFIRMATION} to {@link
 * OrderStatus#CONFIRMED} or {@link OrderStatus#REJECTED}. The transition is guarded by an
 * idempotency ledger (replay-safe) and a status check (no double-transition of an already-terminal
 * or legacy order). A REJECTED result is terminal — no republish, cart-restore, or staff-review
 * (D-11).
 */
@Service
@RequiredArgsConstructor
public class OrderConfirmationService {

  /** Ledger consumer identity for the order-side stock-result handler. */
  static final String CONSUMER_NAME = "order-stock-result";

  private final OrderProcessedEventRepository processedEventRepository;
  private final OrderRepository orderRepository;

  @Transactional
  public void onStockResult(OrderStockResultEvent event) {
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
      // Concurrent delivery inserted the same (eventId, consumer) first — treat as a replay.
      return;
    }

    // (2) Load + status guard: only a PENDING_CONFIRMATION order may transition.
    Optional<OrderEntity> maybeOrder = orderRepository.findById(event.orderId());
    if (maybeOrder.isEmpty()) {
      return;
    }
    OrderEntity order = maybeOrder.get();
    if (order.getStatus() != OrderStatus.PENDING_CONFIRMATION) {
      return;
    }

    // (3) Apply the verdict.
    if (event.result() == Result.CONFIRMED) {
      order.setStatus(OrderStatus.CONFIRMED);
    } else {
      order.setStatus(OrderStatus.REJECTED);
      order.setRejectionReason(describe(event.shortfalls()));
    }
  }

  private String describe(List<Shortfall> shortfalls) {
    if (shortfalls == null || shortfalls.isEmpty()) {
      return "Insufficient stock";
    }
    return "Insufficient stock: "
        + shortfalls.stream()
            .map(
                s ->
                    s.ingredientName()
                        + " (required "
                        + s.required()
                        + ", available "
                        + s.available()
                        + ")")
            .collect(Collectors.joining(", "));
  }
}
