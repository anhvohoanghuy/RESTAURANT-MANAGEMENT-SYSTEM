package com.example.feat1.DDD.kitchen_context.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares {@link NewTopic} beans for kitchen's own genuinely new topics: the inbound {@code
 * orders.confirmed} topic (produced by order_context, consumed here) and the outbound {@code
 * kitchen.ticket-status-changed} topic, plus their {@code .DLT} dead-letter siblings.
 *
 * <p>Anti-pattern guard: the {@code kitchen.settlement-trigger} topic's {@link NewTopic} beans are
 * already declared by {@code InventoryKafkaTopicConfig} — this config intentionally does NOT
 * redeclare a bean for that topic, even though kitchen produces to it, to avoid duplicate {@link
 * NewTopic} bean definitions for the same topic name.
 */
@Configuration
public class KitchenKafkaTopicConfig {

  private static final String DLT_SUFFIX = ".DLT";

  private final String orderConfirmedTopic;
  private final String ticketStatusChangedTopic;

  public KitchenKafkaTopicConfig(
      @Value("${order.events.order-confirmed-topic:orders.confirmed}") String orderConfirmedTopic,
      @Value("${kitchen.events.ticket-status-changed-topic:kitchen.ticket-status-changed}")
          String ticketStatusChangedTopic) {
    this.orderConfirmedTopic = orderConfirmedTopic;
    this.ticketStatusChangedTopic = ticketStatusChangedTopic;
  }

  @Bean
  public NewTopic orderConfirmedTopic() {
    return TopicBuilder.name(orderConfirmedTopic).partitions(1).replicas(1).build();
  }

  @Bean
  public NewTopic orderConfirmedDltTopic() {
    return TopicBuilder.name(orderConfirmedTopic + DLT_SUFFIX).partitions(1).replicas(1).build();
  }

  @Bean
  public NewTopic ticketStatusChangedTopic() {
    return TopicBuilder.name(ticketStatusChangedTopic).partitions(1).replicas(1).build();
  }

  @Bean
  public NewTopic ticketStatusChangedDltTopic() {
    return TopicBuilder.name(ticketStatusChangedTopic + DLT_SUFFIX)
        .partitions(1)
        .replicas(1)
        .build();
  }
}
