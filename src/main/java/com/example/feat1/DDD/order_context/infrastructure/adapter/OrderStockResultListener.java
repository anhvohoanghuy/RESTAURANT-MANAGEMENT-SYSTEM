package com.example.feat1.DDD.order_context.infrastructure.adapter;

import com.example.feat1.DDD.order_context.application.OrderConfirmationService;
import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin inbound adapter (D-10): consumes Inventory's {@link OrderStockResultEvent} verdict off
 * {@code inventory.order-stock-results} and delegates verbatim to {@link OrderConfirmationService}.
 * All idempotency, status-guard, and transition logic lives in the service — this listener stays a
 * one-line delegate (container config: {@code orderStockResultKafkaListenerContainerFactory}).
 */
@Component
@RequiredArgsConstructor
public class OrderStockResultListener {

  private final OrderConfirmationService confirmationService;

  @KafkaListener(
      topics = "${inventory.events.order-stock-results-topic:inventory.order-stock-results}",
      groupId = "${order.stock-result.consumer.group-id:order-stock-result}",
      containerFactory = "orderStockResultKafkaListenerContainerFactory")
  public void onStockResult(OrderStockResultEvent event) {
    confirmationService.onStockResult(event);
  }
}
