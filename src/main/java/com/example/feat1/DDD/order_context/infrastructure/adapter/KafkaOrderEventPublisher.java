package com.example.feat1.DDD.order_context.infrastructure.adapter;

import com.example.feat1.DDD.order_context.application.event.OrderCreatedEvent;
import com.example.feat1.DDD.order_context.domain.port.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaOrderEventPublisher implements OrderEventPublisher {
  private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

  @Value("${order.events.order-created-topic:orders.created}")
  private String orderCreatedTopic;

  @Override
  public void publishOrderCreated(OrderCreatedEvent event) {
    kafkaTemplate.send(orderCreatedTopic, event.orderId().toString(), event);
  }
}
