package com.example.feat1.DDD.order_context.infrastructure.adapter;

import com.example.feat1.DDD.order_context.application.event.OrderConfirmedEvent;
import com.example.feat1.DDD.order_context.application.event.OrderCreatedEvent;
import com.example.feat1.DDD.order_context.domain.port.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaOrderEventPublisher implements OrderEventPublisher {
  private static final Logger log = LoggerFactory.getLogger(KafkaOrderEventPublisher.class);

  private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
  private final KafkaTemplate<String, OrderConfirmedEvent> orderConfirmedKafkaTemplate;

  @Value("${order.events.order-created-topic:orders.created}")
  private String orderCreatedTopic;

  @Value("${order.events.order-confirmed-topic:orders.confirmed}")
  private String orderConfirmedTopic;

  @Override
  public void publishOrderCreated(OrderCreatedEvent event) {
    kafkaTemplate
        .send(orderCreatedTopic, event.orderId().toString(), event)
        .whenComplete(
            (result, ex) -> {
              if (ex != null) {
                log.error(
                    "Order created publish FAILED topic={} order={}",
                    orderCreatedTopic,
                    event.orderId(),
                    ex);
              }
            });
  }

  @Override
  public void publishOrderConfirmed(OrderConfirmedEvent event) {
    orderConfirmedKafkaTemplate
        .send(orderConfirmedTopic, event.orderId().toString(), event)
        .whenComplete(
            (result, ex) -> {
              if (ex != null) {
                log.error(
                    "Order confirmed publish FAILED topic={} order={}",
                    orderConfirmedTopic,
                    event.orderId(),
                    ex);
              }
            });
  }
}
