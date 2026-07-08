package com.example.feat1.DDD.kitchen_context.infrastructure.config;

import com.example.feat1.DDD.order_context.application.event.OrderConfirmedEvent;
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
 * Kitchen-side Kafka consumer wiring for D-01: consumes order_context's {@link OrderConfirmedEvent}
 * off {@code orders.confirmed} and delegates to {@code KitchenTicketCreationService} (via {@code
 * OrderConfirmedListener}).
 *
 * <p>Poison-pill safety: the value deserializer is an {@link ErrorHandlingDeserializer} wrapping
 * the Jackson-3 {@link JacksonJsonDeserializer}. A deserialization failure is classified
 * not-retryable and routed straight to {@code orders.confirmed.DLT} instead of blocking the
 * partition; handler failures retry three times then land on the DLT (T-17-05/T-17-07).
 *
 * <p>Trust boundary (T-17-05): {@code USE_TYPE_INFO_HEADERS=false} + a forced {@code
 * VALUE_DEFAULT_TYPE} + a {@code TRUSTED_PACKAGES} allow-list prevent a malicious {@code
 * __TypeId__} header from instantiating an arbitrary class.
 *
 * <p>All bean names are kitchen-prefixed (Pitfall 5) to avoid collisions with the order/inventory
 * consumer configs' own {@code dltKafkaTemplate}/{@code consumerFactory}-style bean names.
 */
@Configuration
@EnableKafka
public class OrderConfirmedKafkaConsumerConfig {

  private static final String TRUSTED_PACKAGE =
      "com.example.feat1.DDD.order_context.application.event";

  @Bean
  public ConsumerFactory<String, OrderConfirmedEvent> orderConfirmedConsumerFactory(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
      @Value("${kitchen.order-confirmed.consumer.group-id:kitchen-order-confirmed}")
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
    props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, OrderConfirmedEvent.class.getName());
    props.put(
        JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false); // ignore __TypeId__, force type
    return new DefaultKafkaConsumerFactory<>(props);
  }

  /**
   * Dedicated DLT producer template, kitchen-prefixed (Pitfall 5): multiple {@link KafkaTemplate}
   * beans exist app-wide (e.g. {@code orderDltKafkaTemplate}, {@code inventoryDltKafkaTemplate}),
   * so callers must qualify by this name to avoid ambiguity.
   */
  @Bean
  public KafkaTemplate<String, Object> kitchenDltKafkaTemplate(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }

  @Bean
  public DefaultErrorHandler orderConfirmedErrorHandler(
      @Qualifier("kitchenDltKafkaTemplate") KafkaTemplate<String, Object> kitchenDltKafkaTemplate) {
    // Publishes the failed record to "<originalTopic>.DLT" (orders.confirmed.DLT), same
    // partition, preserving exception headers.
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(kitchenDltKafkaTemplate);
    // 3 retries, 1s apart, then recover to the DLT.
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    // Deserialization exceptions are NOT retryable -> straight to DLT (T-17-07).
    handler.addNotRetryableExceptions(DeserializationException.class);
    return handler;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, OrderConfirmedEvent>
      orderConfirmedKafkaListenerContainerFactory(
          ConsumerFactory<String, OrderConfirmedEvent> orderConfirmedConsumerFactory,
          DefaultErrorHandler orderConfirmedErrorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, OrderConfirmedEvent> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(orderConfirmedConsumerFactory);
    factory.setCommonErrorHandler(orderConfirmedErrorHandler);
    // AckMode.RECORD: commit the offset only after a successful, committed handler run.
    factory.getContainerProperties().setAckMode(AckMode.RECORD);
    return factory;
  }
}
