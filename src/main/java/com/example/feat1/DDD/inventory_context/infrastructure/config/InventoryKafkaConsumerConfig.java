package com.example.feat1.DDD.inventory_context.infrastructure.config;

import com.example.feat1.DDD.order_context.application.event.OrderCreatedEvent;
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
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Consumer wiring for the Inventory context's {@code OrderCreated} listener (D-04/D-05). Mirrors
 * the per-context producer-config style with RESEARCH Pattern 1 (typed {@link ConsumerFactory} +
 * {@link ConcurrentKafkaListenerContainerFactory}) and Pattern 2 (a {@link DefaultErrorHandler}
 * backed by a {@link DeadLetterPublishingRecoverer} to {@code <topic>.DLT}).
 *
 * <p>Serde uses the phase-wide Jackson-3 {@link JacksonJsonDeserializer} (established in 15-01)
 * rather than the legacy Jackson-2 {@code JsonDeserializer}: {@code OrderCreatedEvent} carries
 * {@code java.time.Instant} fields that the Jackson-2 deserializer cannot handle on this Boot 4 /
 * Jackson 3 classpath. The deserializer is wrapped in an {@link ErrorHandlingDeserializer} so a
 * poison-pill payload becomes a recoverable record routed to the DLT instead of blocking the
 * partition (T-15-08). {@code USE_TYPE_INFO_HEADERS=false} + a fixed {@code VALUE_DEFAULT_TYPE}
 * plus a {@code TRUSTED_PACKAGES} allow-list defeat malicious {@code __TypeId__} headers (T-15-07).
 */
@Configuration
@EnableKafka
public class InventoryKafkaConsumerConfig {

  @Bean
  public ConsumerFactory<String, OrderCreatedEvent> orderCreatedConsumerFactory(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
      @Value("${inventory.order-created.consumer.group-id:inventory-order-created}")
          String groupId) {
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
        "com.example.feat1.DDD.order_context.application.event");
    props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, OrderCreatedEvent.class.getName());
    // Ignore the producer __TypeId__ header and force the local default type (T-15-07).
    props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
    return new DefaultKafkaConsumerFactory<>(props);
  }

  /**
   * Dedicated producer for dead-letter republishing. Named distinctly because multiple {@code
   * KafkaTemplate} beans exist app-wide (W-4). Uses the Jackson-3 serializer so a
   * successfully-deserialized-but-handler-failed {@code OrderCreatedEvent} (which carries {@code
   * Instant}) can be re-serialized onto the DLT.
   */
  @Bean
  public KafkaTemplate<String, Object> inventoryDltKafkaTemplate(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }

  @Bean
  public DefaultErrorHandler orderCreatedErrorHandler(
      @Qualifier("inventoryDltKafkaTemplate") KafkaTemplate<String, Object> dlt) {
    // Publishes the failed record to "<originalTopic>.DLT" (default destination resolver).
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dlt);
    // 3 retries, 1s apart, then recover to the DLT.
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    // A deserialization failure is a poison pill: do not retry, route straight to the DLT (D-04).
    handler.addNotRetryableExceptions(DeserializationException.class);
    return handler;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent>
      orderCreatedKafkaListenerContainerFactory(
          ConsumerFactory<String, OrderCreatedEvent> orderCreatedConsumerFactory,
          DefaultErrorHandler orderCreatedErrorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(orderCreatedConsumerFactory);
    factory.setCommonErrorHandler(orderCreatedErrorHandler);
    // Commit the offset only after a successful, committed handler run.
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    return factory;
  }
}
