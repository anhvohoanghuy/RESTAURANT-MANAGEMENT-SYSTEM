package com.example.feat1.DDD.kitchen_context.infrastructure.adapter;

import com.example.feat1.DDD.kitchen_context.application.event.KitchenTicketStatusChangedEvent;
import com.example.feat1.DDD.kitchen_context.domain.port.KitchenTicketStatusChangedPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaKitchenTicketStatusChangedPublisher
    implements KitchenTicketStatusChangedPublisher {
  private final KafkaTemplate<String, KitchenTicketStatusChangedEvent>
      ticketStatusChangedKafkaTemplate;

  @Value("${kitchen.events.ticket-status-changed-topic:kitchen.ticket-status-changed}")
  private String ticketStatusChangedTopic;

  @Override
  public void publishTicketStatusChanged(KitchenTicketStatusChangedEvent event) {
    ticketStatusChangedKafkaTemplate.send(
        ticketStatusChangedTopic, event.orderId().toString(), event);
  }
}
