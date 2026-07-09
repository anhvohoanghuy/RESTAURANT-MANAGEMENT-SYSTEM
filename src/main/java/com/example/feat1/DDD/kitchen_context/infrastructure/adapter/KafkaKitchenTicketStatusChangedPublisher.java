package com.example.feat1.DDD.kitchen_context.infrastructure.adapter;

import com.example.feat1.DDD.kitchen_context.application.event.KitchenTicketStatusChangedEvent;
import com.example.feat1.DDD.kitchen_context.domain.port.KitchenTicketStatusChangedPublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaKitchenTicketStatusChangedPublisher
    implements KitchenTicketStatusChangedPublisher {
  private static final Logger log =
      LoggerFactory.getLogger(KafkaKitchenTicketStatusChangedPublisher.class);

  private final KafkaTemplate<String, KitchenTicketStatusChangedEvent>
      ticketStatusChangedKafkaTemplate;

  @Value("${kitchen.events.ticket-status-changed-topic:kitchen.ticket-status-changed}")
  private String ticketStatusChangedTopic;

  @Override
  public void publishTicketStatusChanged(KitchenTicketStatusChangedEvent event) {
    ticketStatusChangedKafkaTemplate
        .send(ticketStatusChangedTopic, event.orderId().toString(), event)
        .whenComplete(
            (result, ex) -> {
              if (ex != null) {
                log.error(
                    "Ticket status changed publish FAILED topic={} order={} ticket={}",
                    ticketStatusChangedTopic,
                    event.orderId(),
                    event.ticketId(),
                    ex);
              }
            });
  }
}
