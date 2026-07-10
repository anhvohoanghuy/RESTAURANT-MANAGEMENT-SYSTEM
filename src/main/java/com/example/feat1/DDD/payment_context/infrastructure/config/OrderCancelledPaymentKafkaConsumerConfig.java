package com.example.feat1.DDD.payment_context.infrastructure.config;

import com.example.feat1.DDD.order_context.application.event.OrderCancelledEvent;
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
 * Payment-side Kafka consumer wiring for CANCEL-06: consumes order_context's {@link
 * OrderCancelledEvent} off {@code orders.cancelled} and delegates to {@code
 * PaymentAutoRefundService} (via {@code OrderCancelledPaymentListener}).
 *
 * <p>Cross-context trust: the event class lives in {@code order_context.application.event}, so the
 * trusted package and default type are pinned to that package/class rather than this consumer's own
 * package (mirrors {@code TicketStatusChangedKafkaConsumerConfig}, T-17-18 pattern).
 *
 * <p>Poison-pill safety: the value deserializer is an {@link ErrorHandlingDeserializer} wrapping
 * the Jackson-3 {@link JacksonJsonDeserializer}. A deserialization failure is classified
 * not-retryable and routed straight to {@code orders.cancelled.DLT} instead of blocking the
 * partition; handler failures retry three times then land on the DLT.
 *
 * <p>This is Payment's FIRST-ever Kafka consumer, so unlike other contexts it has no existing
 * {@code @EnableKafka}-bearing configuration or DLT {@link KafkaTemplate} to reuse — both are
 * declared here.
 *
 * <p>All bean names are distinctly prefixed ({@code orderCancelledPayment*}) to avoid collisions
 * with other consumer configs already registered app-wide (Pitfall 5).
 */
@Configuration
@EnableKafka
public class OrderCancelledPaymentKafkaConsumerConfig {

  private static final String TRUSTED_PACKAGE =
      "com.example.feat1.DDD.order_context.application.event";

  @Bean
  public ConsumerFactory<String, OrderCancelledEvent> orderCancelledPaymentConsumerFactory(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
      @Value("${payment.order-cancelled.consumer.group-id:payment-order-cancelled}")
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

  /**
   * Dedicated DLT producer template. Payment has no existing DLT template to reuse (this is its
   * first consumer), so one is declared here using Jackson-3 {@link JacksonJsonSerializer}.
   */
  @Bean
  public KafkaTemplate<String, Object> orderCancelledPaymentDltKafkaTemplate(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }

  @Bean
  public DefaultErrorHandler orderCancelledPaymentErrorHandler(
      @Qualifier("orderCancelledPaymentDltKafkaTemplate")
          KafkaTemplate<String, Object> orderCancelledPaymentDltKafkaTemplate) {
    // Publishes the failed record to "<originalTopic>.DLT" (orders.cancelled.DLT), same
    // partition, preserving exception headers.
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(orderCancelledPaymentDltKafkaTemplate);
    // 3 retries, 1s apart, then recover to the DLT.
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    // Deserialization exceptions are NOT retryable -> straight to DLT (poison-pill safety).
    handler.addNotRetryableExceptions(DeserializationException.class);
    return handler;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, OrderCancelledEvent>
      orderCancelledPaymentKafkaListenerContainerFactory(
          ConsumerFactory<String, OrderCancelledEvent> orderCancelledPaymentConsumerFactory,
          DefaultErrorHandler orderCancelledPaymentErrorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, OrderCancelledEvent> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(orderCancelledPaymentConsumerFactory);
    factory.setCommonErrorHandler(orderCancelledPaymentErrorHandler);
    // AckMode.RECORD: commit the offset only after a successful, committed handler run.
    factory.getContainerProperties().setAckMode(AckMode.RECORD);
    return factory;
  }
}
