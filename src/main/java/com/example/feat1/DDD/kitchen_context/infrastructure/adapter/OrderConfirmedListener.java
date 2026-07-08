package com.example.feat1.DDD.kitchen_context.infrastructure.adapter;

import com.example.feat1.DDD.kitchen_context.application.KitchenTicketCreationService;
import com.example.feat1.DDD.order_context.application.event.OrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin inbound adapter (D-01): consumes order_context's {@link OrderConfirmedEvent} off {@code
 * orders.confirmed} and delegates verbatim to {@link KitchenTicketCreationService}. All idempotency
 * and ticket-creation logic lives in the service — this listener stays a one-line delegate
 * (container config: {@code orderConfirmedKafkaListenerContainerFactory}).
 */
@Component
@RequiredArgsConstructor
public class OrderConfirmedListener {

  private final KitchenTicketCreationService creationService;

  @KafkaListener(
      topics = "${order.events.order-confirmed-topic:orders.confirmed}",
      groupId = "${kitchen.order-confirmed.consumer.group-id:kitchen-order-confirmed}",
      containerFactory = "orderConfirmedKafkaListenerContainerFactory")
  public void onOrderConfirmed(OrderConfirmedEvent event) {
    creationService.onOrderConfirmed(event);
  }
}
