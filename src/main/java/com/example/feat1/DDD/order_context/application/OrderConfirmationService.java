package com.example.feat1.DDD.order_context.application;

import com.example.feat1.DDD.order_context.application.event.OrderConfirmedEvent;
import com.example.feat1.DDD.order_context.application.event.OrderConfirmedEvent.OrderConfirmedLine;
import com.example.feat1.DDD.order_context.application.event.OrderConfirmedEvent.OrderConfirmedTopping;
import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent;
import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent.Result;
import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent.Shortfall;
import com.example.feat1.DDD.order_context.domain.model.OrderStatus;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderLineEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderProcessedEventEntity;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderProcessedEventRepository;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderRepository;
import com.example.feat1.DDD.shared.outbox.application.OutboxWriter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

  /** Cap on the persisted rejection reason (T-17.1-18 / I-WR-04) — column is widened to match. */
  static final int MAX_REASON_LEN = 60000;

  private static final String TRUNCATION_SUFFIX = "... (truncated)";

  private final OrderProcessedEventRepository processedEventRepository;
  private final OrderRepository orderRepository;
  private final OutboxWriter outboxWriter;

  @Value("${order.events.order-confirmed-topic:orders.confirmed}")
  private String orderConfirmedTopic;

  @Transactional
  public void onStockResult(OrderStockResultEvent event) {
    // (1) Idempotency fast pre-check: absorb an already-recorded replay cheaply. The authoritative
    // guard is the ledger insert at the END of this method (I-WR-01 / CR-01 fix).
    if (processedEventRepository.existsByEventIdAndConsumerName(event.eventId(), CONSUMER_NAME)) {
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
      outboxWriter.save(
          "ORDER",
          order.getId(),
          OrderConfirmedEvent.TYPE,
          orderConfirmedTopic,
          order.getId().toString(),
          toOrderConfirmedEvent(order));
    } else {
      order.setStatus(OrderStatus.REJECTED);
      order.setRejectionReason(describe(event.shortfalls()));
    }

    // (4) Record the idempotency-ledger row LAST, in THIS transaction, so it commits atomically
    // with the status transition and any outbox row. A concurrent-duplicate unique violation now
    // rolls back the WHOLE transaction — Kafka redelivers and the pre-check above absorbs the
    // replay — instead of pre-committing a "processed" marker in a separate REQUIRES_NEW
    // transaction ahead of unguaranteed business work, which could strand the order forever
    // (CR-01 / I-WR-01 fix).
    recordProcessed(event.eventId());
  }

  private void recordProcessed(UUID eventId) {
    OrderProcessedEventEntity ledger = new OrderProcessedEventEntity();
    ledger.setEventId(eventId);
    ledger.setConsumerName(CONSUMER_NAME);
    ledger.setProcessedAt(Instant.now());
    processedEventRepository.save(ledger);
  }

  private OrderConfirmedEvent toOrderConfirmedEvent(OrderEntity order) {
    List<OrderConfirmedLine> lines =
        order.getLines().stream().map(this::toOrderConfirmedLine).toList();
    return new OrderConfirmedEvent(
        UUID.randomUUID(), OrderConfirmedEvent.TYPE, Instant.now(), order.getId(), lines);
  }

  private OrderConfirmedLine toOrderConfirmedLine(OrderLineEntity line) {
    List<OrderConfirmedTopping> toppings =
        line.getSelectedToppings().stream()
            .map(
                topping ->
                    new OrderConfirmedTopping(
                        topping.getToppingGroupId(),
                        topping.getToppingGroupName(),
                        topping.getToppingOptionId(),
                        topping.getToppingOptionName()))
            .toList();
    return new OrderConfirmedLine(
        line.getId(), line.getDishId(), line.getDishName(), line.getQuantity(), toppings);
  }

  private String describe(List<Shortfall> shortfalls) {
    String description;
    if (shortfalls == null || shortfalls.isEmpty()) {
      description = "Insufficient stock";
    } else {
      description =
          "Insufficient stock: "
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
    return truncate(description);
  }

  /**
   * Caps the persisted rejection reason at {@link #MAX_REASON_LEN} (T-17.1-18 / I-WR-04). A
   * multi-ingredient rejection can otherwise produce an unbounded string; the column is widened to
   * TEXT AND the application layer bounds the value defensively.
   */
  private String truncate(String description) {
    if (description.length() <= MAX_REASON_LEN) {
      return description;
    }
    return description.substring(0, MAX_REASON_LEN - TRUNCATION_SUFFIX.length())
        + TRUNCATION_SUFFIX;
  }
}
