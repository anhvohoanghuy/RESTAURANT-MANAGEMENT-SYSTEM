package com.example.feat1.DDD.kitchen_context.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.feat1.DDD.order_context.application.event.OrderConfirmedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

/**
 * Broker-free wiring test for {@link OrderConfirmedKafkaConsumerConfig}: instantiates the config
 * directly (no Spring context, no live broker) and asserts the consumer factory, container factory,
 * and error-handler beans are wired for at-least-once + poison-pill safety (D-01, T-17-05/T-17-07),
 * mirroring {@code SettleTriggerKafkaConsumerConfigTest}.
 */
class OrderConfirmedKafkaConsumerConfigTest {

  private static final String BOOTSTRAP = "localhost:9092";
  private static final String GROUP_ID = "kitchen-order-confirmed";

  private final OrderConfirmedKafkaConsumerConfig config = new OrderConfirmedKafkaConsumerConfig();

  @Test
  void consumerFactoryDisablesAutoCommitAndForcesSafeDeserialization() {
    ConsumerFactory<String, OrderConfirmedEvent> cf =
        config.orderConfirmedConsumerFactory(BOOTSTRAP, GROUP_ID);

    assertThat(cf).isNotNull();
    var props = cf.getConfigurationProperties();
    // Container manages acks (at-least-once), never the client's auto-commit.
    assertThat(props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isEqualTo(false);
    assertThat(props.get(ConsumerConfig.GROUP_ID_CONFIG)).isEqualTo(GROUP_ID);
    // Poison-pill safety: ErrorHandlingDeserializer wrapping the Jackson-3 deserializer.
    assertThat(props.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG))
        .isEqualTo(ErrorHandlingDeserializer.class);
    assertThat(props.get(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS))
        .isEqualTo(JacksonJsonDeserializer.class);
    // Type-confusion defence (T-17-05): ignore producer headers, force the local default type.
    assertThat(props.get(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS)).isEqualTo(false);
    assertThat(props.get(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE))
        .isEqualTo(OrderConfirmedEvent.class.getName());
    assertThat(props.get(JacksonJsonDeserializer.TRUSTED_PACKAGES))
        .isEqualTo("com.example.feat1.DDD.order_context.application.event");
  }

  @Test
  void containerFactoryUsesRecordAckMode() {
    ConsumerFactory<String, OrderConfirmedEvent> cf =
        config.orderConfirmedConsumerFactory(BOOTSTRAP, GROUP_ID);
    DefaultErrorHandler handler =
        config.orderConfirmedErrorHandler(config.kitchenDltKafkaTemplate(BOOTSTRAP));

    ConcurrentKafkaListenerContainerFactory<String, OrderConfirmedEvent> factory =
        config.orderConfirmedKafkaListenerContainerFactory(cf, handler);

    assertThat(factory).isNotNull();
    assertThat(factory.getContainerProperties().getAckMode())
        .isEqualTo(ContainerProperties.AckMode.RECORD);
  }

  @Test
  void errorHandlerMarksDeserializationExceptionNotRetryable() {
    KafkaTemplate<String, Object> dlt = config.kitchenDltKafkaTemplate(BOOTSTRAP);
    assertThat(dlt).isNotNull();

    DefaultErrorHandler handler = config.orderConfirmedErrorHandler(dlt);
    assertThat(handler).isNotNull();

    // removeClassification returns the prior classification: FALSE == not-retryable (straight to
    // DLT).
    assertThat(handler.removeClassification(DeserializationException.class)).isFalse();
  }
}
