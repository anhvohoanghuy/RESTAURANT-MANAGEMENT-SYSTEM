package com.example.feat1.DDD.inventory_context.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the saga topics as {@link NewTopic} beans so a broker without {@code
 * auto.create.topics.enable} still works (RESEARCH Open Q4). Boot auto-configures a {@code
 * KafkaAdmin} from {@code spring.kafka.bootstrap-servers}, which declares these beans when a broker
 * is reachable and leaves them inert otherwise.
 *
 * <p>Topics: the result topic {@code inventory.order-stock-results}, and the two dead-letter topics
 * {@code orders.created.DLT} (poison-pill / handler-failure sink for the OrderCreated consumer) and
 * {@code inventory.order-stock-results.DLT}. The DLT names are derived from the same {@code @Value}
 * properties as the live topics so they always match the actual topic names.
 */
@Configuration
public class InventoryKafkaTopicConfig {

  private static final String DLT_SUFFIX = ".DLT";

  private final String orderStockResultsTopic;
  private final String orderCreatedTopic;
  private final String settleTriggerTopic;

  public InventoryKafkaTopicConfig(
      @Value("${inventory.events.order-stock-results-topic:inventory.order-stock-results}")
          String orderStockResultsTopic,
      @Value("${order.events.order-created-topic:orders.created}") String orderCreatedTopic,
      @Value("${kitchen.events.settle-trigger-topic:kitchen.settlement-trigger}")
          String settleTriggerTopic) {
    this.orderStockResultsTopic = orderStockResultsTopic;
    this.orderCreatedTopic = orderCreatedTopic;
    this.settleTriggerTopic = settleTriggerTopic;
  }

  @Bean
  public NewTopic orderStockResultsTopic() {
    return TopicBuilder.name(orderStockResultsTopic).partitions(1).replicas(1).build();
  }

  @Bean
  public NewTopic orderCreatedDltTopic() {
    return TopicBuilder.name(orderCreatedTopic + DLT_SUFFIX).partitions(1).replicas(1).build();
  }

  @Bean
  public NewTopic orderStockResultsDltTopic() {
    return TopicBuilder.name(orderStockResultsTopic + DLT_SUFFIX).partitions(1).replicas(1).build();
  }

  @Bean
  public NewTopic settleTriggerTopic() {
    return TopicBuilder.name(settleTriggerTopic).partitions(1).replicas(1).build();
  }

  @Bean
  public NewTopic settleTriggerDltTopic() {
    return TopicBuilder.name(settleTriggerTopic + DLT_SUFFIX).partitions(1).replicas(1).build();
  }
}
