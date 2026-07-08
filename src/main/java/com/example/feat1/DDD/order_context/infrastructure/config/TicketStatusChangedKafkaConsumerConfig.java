package com.example.feat1.DDD.order_context.infrastructure.config;

import com.example.feat1.DDD.kitchen_context.application.event.KitchenTicketStatusChangedEvent;
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
 * Order-side Kafka consumer wiring for the fulfillment-loop closer (D-04): consumes kitchen's
 * {@link KitchenTicketStatusChangedEvent} off {@code kitchen.ticket-status-changed} and delegates
 * to {@code KitchenStatusProjectionService} (via {@code TicketStatusChangedListener}).
 *
 * <p>Cross-context trust: the event class lives in {@code kitchen_context.application.event}, so
 * the trusted package and default type are pinned to that package/class rather than this consumer's
 * own package (T-17-18).
 *
 * <p>Poison-pill safety: the value deserializer is an {@link ErrorHandlingDeserializer} wrapping
 * the Jackson-3 {@link JacksonJsonDeserializer}. A deserialization failure is classified
 * not-retryable and routed straight to {@code kitchen.ticket-status-changed.DLT} instead of
 * blocking the partition; handler failures retry three times then land on the DLT.
 *
 * <p>All bean names are distinctly prefixed ({@code ticketStatusChanged*}) to avoid collisions with
 * other consumer configs already registered app-wide (Pitfall 5).
 */
@Configuration
@EnableKafka
public class TicketStatusChangedKafkaConsumerConfig {

  private static final String TRUSTED_PACKAGE =
      "com.example.feat1.DDD.kitchen_context.application.event";

  @Bean
  public ConsumerFactory<String, KitchenTicketStatusChangedEvent>
      ticketStatusChangedConsumerFactory(
          @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
          @Value("${order.ticket-status-changed.consumer.group-id:order-ticket-status-changed}")
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
    props.put(
        JacksonJsonDeserializer.VALUE_DEFAULT_TYPE,
        KitchenTicketStatusChangedEvent.class.getName());
    props.put(
        JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false); // ignore __TypeId__, force type
    return new DefaultKafkaConsumerFactory<>(props);
  }

  /**
   * Dedicated DLT producer template, distinctly named to avoid ambiguity with other {@link
   * KafkaTemplate} beans already registered app-wide.
   */
  @Bean
  public KafkaTemplate<String, Object> ticketStatusChangedDltKafkaTemplate(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }

  @Bean
  public DefaultErrorHandler ticketStatusChangedErrorHandler(
      @Qualifier("ticketStatusChangedDltKafkaTemplate")
          KafkaTemplate<String, Object> ticketStatusChangedDltKafkaTemplate) {
    // Publishes the failed record to "<originalTopic>.DLT" (kitchen.ticket-status-changed.DLT),
    // same partition, preserving exception headers.
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(ticketStatusChangedDltKafkaTemplate);
    // 3 retries, 1s apart, then recover to the DLT.
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    // Deserialization exceptions are NOT retryable -> straight to DLT (poison-pill safety,
    // T-17-18).
    handler.addNotRetryableExceptions(DeserializationException.class);
    return handler;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, KitchenTicketStatusChangedEvent>
      ticketStatusChangedKafkaListenerContainerFactory(
          ConsumerFactory<String, KitchenTicketStatusChangedEvent>
              ticketStatusChangedConsumerFactory,
          DefaultErrorHandler ticketStatusChangedErrorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, KitchenTicketStatusChangedEvent> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(ticketStatusChangedConsumerFactory);
    factory.setCommonErrorHandler(ticketStatusChangedErrorHandler);
    // AckMode.RECORD: commit the offset only after a successful, committed handler run.
    factory.getContainerProperties().setAckMode(AckMode.RECORD);
    return factory;
  }
}
