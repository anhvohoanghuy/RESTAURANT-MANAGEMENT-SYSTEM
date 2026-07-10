package com.example.feat1.DDD.payment_context.infrastructure.adapter;

import com.example.feat1.DDD.order_context.application.event.OrderCancelledEvent;
import com.example.feat1.DDD.payment_context.application.PaymentAutoRefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin inbound adapter: consumes order_context's {@link OrderCancelledEvent} off {@code
 * orders.cancelled} and delegates verbatim to {@link PaymentAutoRefundService}. All idempotency,
 * whole-order gating, and refund-iteration logic lives in the service — this listener stays a
 * one-line delegate (container config: {@code orderCancelledPaymentKafkaListenerContainerFactory}).
 */
@Component
@RequiredArgsConstructor
public class OrderCancelledPaymentListener {

  private final PaymentAutoRefundService autoRefundService;

  @KafkaListener(
      topics = "${order.events.order-cancelled-topic:orders.cancelled}",
      groupId = "${payment.order-cancelled.consumer.group-id:payment-order-cancelled}",
      containerFactory = "orderCancelledPaymentKafkaListenerContainerFactory")
  public void onOrderCancelled(OrderCancelledEvent event) {
    autoRefundService.onOrderCancelled(event);
  }
}
