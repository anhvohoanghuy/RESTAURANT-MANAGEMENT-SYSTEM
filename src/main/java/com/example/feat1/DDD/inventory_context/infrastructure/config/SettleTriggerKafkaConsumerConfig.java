package com.example.feat1.DDD.inventory_context.infrastructure.config;

import com.example.feat1.DDD.inventory_context.application.event.SettleTriggerEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Consumer wiring for the Inventory context's {@code SettleTrigger} listener (D-01/D-05). Mirrors
 * {@link InventoryKafkaConsumerConfig} bean-for-bean — RESEARCH Pattern 1 (typed {@link
 * ConsumerFactory} + {@link ConcurrentKafkaListenerContainerFactory}) and Pattern 2 (a {@link
 * DefaultErrorHandler} backed by a {@link DeadLetterPublishingRecoverer} to {@code <topic>.DLT}).
 *
 * <p>{@code @EnableKafka} is intentionally NOT declared here — it already lives on {@link
 * InventoryKafkaConsumerConfig} for the whole context (IN-03). This class only adds the
 * settle-trigger triplet and reuses the existing {@code inventoryDltKafkaTemplate} for DLT
 * republishing.
 *
 * <p>Serde uses the phase-wide Jackson-3 {@link JacksonJsonDeserializer}: {@code
 * SettleTriggerEvent} carries a {@code java.time.Instant} the legacy Jackson-2 deserializer cannot
 * handle on this Boot 4 / Jackson 3 classpath. The deserializer is wrapped in an {@link
 * ErrorHandlingDeserializer} so a poison-pill payload becomes a recoverable record routed to the
 * DLT instead of blocking the partition (T-16-13). {@code USE_TYPE_INFO_HEADERS=false} + a fixed
 * {@code VALUE_DEFAULT_TYPE} plus a {@code TRUSTED_PACKAGES} allow-list defeat malicious {@code
 * __TypeId__} headers (T-16-12).
 */
@Configuration
public class SettleTriggerKafkaConsumerConfig {

  @Bean
  public ConsumerFactory<String, SettleTriggerEvent> settleTriggerConsumerFactory(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
      @Value("${inventory.settlement.consumer.group-id:inventory-settlement}") String groupId) {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // container manages acks
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    // Poison-pill safety: wrap the JSON deserializer so a bad payload is recoverable, not fatal.
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);
    props.put(
        JacksonJsonDeserializer.TRUSTED_PACKAGES,
        "com.example.feat1.DDD.inventory_context.application.event");
    props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, SettleTriggerEvent.class.getName());
    // Ignore the producer __TypeId__ header and force the local default type (T-16-12).
    props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public DefaultErrorHandler settleTriggerErrorHandler(
      @Qualifier("inventoryDltKafkaTemplate") KafkaTemplate<String, Object> dlt) {
    // Publishes the failed record to "<originalTopic>.DLT" (default destination resolver).
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dlt);
    // 3 retries, 1s apart, then recover to the DLT.
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    // A deserialization failure is a poison pill: do not retry, route straight to the DLT (D-01).
    // A missing reservation/order-line is intentionally left retryable so a transient
    // settle-before-
    // reserve ordering race self-heals via retry before hitting the DLT (T-16-14 / D-05).
    handler.addNotRetryableExceptions(DeserializationException.class);
    return handler;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, SettleTriggerEvent>
      settleTriggerKafkaListenerContainerFactory(
          ConsumerFactory<String, SettleTriggerEvent> settleTriggerConsumerFactory,
          DefaultErrorHandler settleTriggerErrorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, SettleTriggerEvent> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(settleTriggerConsumerFactory);
    factory.setCommonErrorHandler(settleTriggerErrorHandler);
    // Commit the offset only after a successful, committed handler run.
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    return factory;
  }
}
