package com.example.feat1.DDD.order_context.infrastructure.adapter;

import com.example.feat1.DDD.kitchen_context.application.event.KitchenTicketStatusChangedEvent;
import com.example.feat1.DDD.order_context.application.KitchenStatusProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin inbound adapter (D-04): consumes kitchen's {@link KitchenTicketStatusChangedEvent} off
 * {@code kitchen.ticket-status-changed} and delegates verbatim to {@link
 * KitchenStatusProjectionService}. All idempotency, status-derivation, and rank-guard logic lives
 * in the service -- this listener stays a one-line delegate (container config: {@code
 * ticketStatusChangedKafkaListenerContainerFactory}).
 */
@Component
@RequiredArgsConstructor
public class TicketStatusChangedListener {

  private final KitchenStatusProjectionService projectionService;

  @KafkaListener(
      topics = "${kitchen.events.ticket-status-changed-topic:kitchen.ticket-status-changed}",
      groupId = "${order.ticket-status-changed.consumer.group-id:order-ticket-status-changed}",
      containerFactory = "ticketStatusChangedKafkaListenerContainerFactory")
  public void onTicketStatusChanged(KitchenTicketStatusChangedEvent event) {
    projectionService.onTicketStatusChanged(event);
  }
}
