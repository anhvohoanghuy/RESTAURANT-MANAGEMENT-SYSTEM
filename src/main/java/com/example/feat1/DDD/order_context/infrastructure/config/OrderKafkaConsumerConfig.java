package com.example.feat1.DDD.order_context.infrastructure.config;

import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Order-side Kafka consumer wiring (D-04/D-05/D-10). Closes the order-confirmation saga by
 * consuming Inventory's {@link OrderStockResultEvent} verdict off {@code
 * inventory.order-stock-results} and delegating to {@code OrderConfirmationService} (via {@code
 * OrderStockResultListener}).
 *
 * <p>Poison-pill safety: the value deserializer is an {@link ErrorHandlingDeserializer} wrapping
 * the Jackson-3 {@link JacksonJsonDeserializer} (the phase-wide serde matching the producer's
 * {@link JacksonJsonSerializer}; the legacy Jackson-2 {@code JsonDeserializer} cannot deserialize
 * the event's {@code Instant occurredAt} on this Boot 4 / Jackson 3 classpath). A deserialization
 * failure is classified not-retryable and routed straight to {@code
 * inventory.order-stock-results.DLT} instead of blocking the partition; handler failures retry
 * three times then land on the DLT.
 *
 * <p>Trust boundary (T-15-07): {@code USE_TYPE_INFO_HEADERS=false} + a forced {@code
 * VALUE_DEFAULT_TYPE} + a {@code TRUSTED_PACKAGES} allow-list prevent a malicious {@code
 * __TypeId__} header from instantiating an arbitrary class.
 *
 * <p>The result topic and its {@code .DLT} are declared as {@code NewTopic} beans in the
 * inventory-side config (Plan 15-04); this config intentionally declares none. All bean names are
 * distinct from the inventory consumer config to avoid collisions.
 */
@Configuration
@EnableKafka
public class OrderKafkaConsumerConfig {

  private static final String TRUSTED_PACKAGE =
      "com.example.feat1.DDD.order_context.application.event";

  @Bean
  public ConsumerFactory<String, OrderStockResultEvent> orderStockResultConsumerFactory(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
      @Value("${order.stock-result.consumer.group-id:order-stock-result}") String groupId) {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // container manages acks
    // Poison-pill safety: wrap the JSON deserializer so a bad payload is recoverable, not fatal.
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);
    props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, TRUSTED_PACKAGE);
    props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, OrderStockResultEvent.class.getName());
    props.put(
        JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false); // ignore __TypeId__, force type
    return new DefaultKafkaConsumerFactory<>(props);
  }

  /**
   * Dedicated DLT producer template. Explicitly named {@code orderDltKafkaTemplate} (W-4): multiple
   * {@link KafkaTemplate} beans exist app-wide (e.g. the inventory consumer's {@code
   * inventoryDltKafkaTemplate}), so callers must qualify by this name to avoid ambiguity.
   */
  @Bean
  public KafkaTemplate<String, Object> orderDltKafkaTemplate(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }

  @Bean
  public DefaultErrorHandler orderStockResultErrorHandler(
      @Qualifier("orderDltKafkaTemplate") KafkaTemplate<String, Object> orderDltKafkaTemplate) {
    // Publishes the failed record to "<originalTopic>.DLT" (inventory.order-stock-results.DLT),
    // same partition, preserving exception headers (T-15-09).
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(orderDltKafkaTemplate);
    // 3 retries, 1s apart, then recover to the DLT.
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    // Deserialization exceptions are NOT retryable -> straight to DLT (poison-pill safety,
    // T-15-08).
    handler.addNotRetryableExceptions(DeserializationException.class);
    return handler;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, OrderStockResultEvent>
      orderStockResultKafkaListenerContainerFactory(
          ConsumerFactory<String, OrderStockResultEvent> orderStockResultConsumerFactory,
          DefaultErrorHandler orderStockResultErrorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, OrderStockResultEvent> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(orderStockResultConsumerFactory);
    factory.setCommonErrorHandler(orderStockResultErrorHandler);
    // AckMode.RECORD: commit the offset only after a successful, committed handler run.
    factory.getContainerProperties().setAckMode(AckMode.RECORD);
    return factory;
  }
}
