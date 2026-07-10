package com.example.feat1.DDD.kitchen_context.infrastructure.config;

import com.example.feat1.DDD.order_context.application.event.OrderCancelledEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kitchen-side Kafka consumer wiring for plan 18-06 (D-7): consumes order_context's {@link
 * OrderCancelledEvent} off {@code orders.cancelled} and delegates to {@code
 * KitchenTicketInvalidationService} (via {@code OrderCancelledKitchenListener}). Bean-for-bean
 * analog of {@link OrderConfirmedKafkaConsumerConfig}, reusing kitchen's EXISTING DLT {@link
 * KafkaTemplate} bean ({@code kitchenDltKafkaTemplate}) rather than declaring a new one (Pitfall 5:
 * multiple {@code KafkaTemplate} beans exist app-wide, one DLT template per kitchen consumer would
 * be redundant).
 *
 * <p>Poison-pill safety: the value deserializer is an {@link ErrorHandlingDeserializer} wrapping
 * the Jackson-3 {@link JacksonJsonDeserializer}. A deserialization failure is classified
 * not-retryable and routed straight to {@code orders.cancelled.DLT} instead of blocking the
 * partition; handler failures retry three times then land on the DLT (mirrors T-17-05/T-17-07).
 *
 * <p>Trust boundary (mirrors T-17-05): {@code USE_TYPE_INFO_HEADERS=false} + a forced {@code
 * VALUE_DEFAULT_TYPE} + a {@code TRUSTED_PACKAGES} allow-list prevent a malicious {@code
 * __TypeId__} header from instantiating an arbitrary class (T-18-06-04).
 *
 * <p>All bean names are kitchen-prefixed / cancelled-scoped to avoid collisions with the
 * order-confirmed consumer config's own bean names.
 */
@Configuration
@EnableKafka
public class OrderCancelledKitchenKafkaConsumerConfig {

  private static final String TRUSTED_PACKAGE =
      "com.example.feat1.DDD.order_context.application.event";

  @Bean
  public ConsumerFactory<String, OrderCancelledEvent> orderCancelledKitchenConsumerFactory(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
      @Value("${kitchen.order-cancelled.consumer.group-id:kitchen-order-cancelled}")
          String groupId) {
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
    props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, OrderCancelledEvent.class.getName());
    props.put(
        JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false); // ignore __TypeId__, force type
    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public DefaultErrorHandler orderCancelledKitchenErrorHandler(
      @Qualifier("kitchenDltKafkaTemplate") KafkaTemplate<String, Object> kitchenDltKafkaTemplate) {
    // Publishes the failed record to "<originalTopic>.DLT" (orders.cancelled.DLT), same
    // partition, preserving exception headers. Reuses the EXISTING kitchen DLT template bean
    // (do not create a new one).
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(kitchenDltKafkaTemplate);
    // 3 retries, 1s apart, then recover to the DLT.
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    // Deserialization exceptions are NOT retryable -> straight to DLT (T-18-06-04).
    handler.addNotRetryableExceptions(DeserializationException.class);
    return handler;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, OrderCancelledEvent>
      orderCancelledKitchenKafkaListenerContainerFactory(
          ConsumerFactory<String, OrderCancelledEvent> orderCancelledKitchenConsumerFactory,
          DefaultErrorHandler orderCancelledKitchenErrorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, OrderCancelledEvent> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(orderCancelledKitchenConsumerFactory);
    factory.setCommonErrorHandler(orderCancelledKitchenErrorHandler);
    // AckMode.RECORD: commit the offset only after a successful, committed handler run.
    factory.getContainerProperties().setAckMode(AckMode.RECORD);
    return factory;
  }
}
