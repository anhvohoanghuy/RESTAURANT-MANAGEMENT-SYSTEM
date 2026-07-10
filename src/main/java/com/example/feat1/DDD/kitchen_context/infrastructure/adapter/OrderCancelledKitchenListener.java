package com.example.feat1.DDD.kitchen_context.infrastructure.adapter;

import com.example.feat1.DDD.kitchen_context.application.KitchenTicketInvalidationService;
import com.example.feat1.DDD.order_context.application.event.OrderCancelledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin inbound adapter (D-01 idiom, plan 18-06 / D-7): consumes order_context's {@link
 * OrderCancelledEvent} off {@code orders.cancelled} and delegates verbatim to {@link
 * KitchenTicketInvalidationService}. All idempotency and void-guard logic lives in the service —
 * this listener stays a one-line delegate (container config: {@code
 * orderCancelledKitchenKafkaListenerContainerFactory}).
 */
@Component
@RequiredArgsConstructor
public class OrderCancelledKitchenListener {

  private final KitchenTicketInvalidationService invalidationService;

  @KafkaListener(
      topics = "${order.events.order-cancelled-topic:orders.cancelled}",
      groupId = "${kitchen.order-cancelled.consumer.group-id:kitchen-order-cancelled}",
      containerFactory = "orderCancelledKitchenKafkaListenerContainerFactory")
  public void onOrderCancelled(OrderCancelledEvent event) {
    invalidationService.onOrderCancelled(event);
  }
}
