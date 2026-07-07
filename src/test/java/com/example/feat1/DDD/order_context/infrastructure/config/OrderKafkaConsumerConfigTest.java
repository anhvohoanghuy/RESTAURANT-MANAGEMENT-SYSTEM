package com.example.feat1.DDD.order_context.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

/**
 * Broker-free unit test for {@link OrderKafkaConsumerConfig}: instantiates the config directly and
 * asserts the beans are wired for poison-pill safety (ErrorHandlingDeserializer -> Jackson-3
 * JacksonJsonDeserializer, forced default type, no type headers), container-managed acks
 * (auto-commit off, {@link AckMode#RECORD}), and a DLT error handler. No broker is contacted.
 */
class OrderKafkaConsumerConfigTest {

  private static final String BOOTSTRAP = "localhost:9092";
  private static final String GROUP = "order-stock-result";

  private final OrderKafkaConsumerConfig config = new OrderKafkaConsumerConfig();

  @Test
  void consumerFactoryDisablesAutoCommitAndUsesErrorHandlingJacksonDeserializer() {
    ConsumerFactory<String, OrderStockResultEvent> cf =
        config.orderStockResultConsumerFactory(BOOTSTRAP, GROUP);
    assertThat(cf).isNotNull();

    Map<String, Object> props =
        ((DefaultKafkaConsumerFactory<String, OrderStockResultEvent>) cf)
            .getConfigurationProperties();

    assertThat(props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isEqualTo(false);
    assertThat(props.get(ConsumerConfig.GROUP_ID_CONFIG)).isEqualTo(GROUP);
    assertThat(props.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo("earliest");
    assertThat(props.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG))
        .isEqualTo(ErrorHandlingDeserializer.class);
    assertThat(props.get(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS))
        .isEqualTo(JacksonJsonDeserializer.class);
    // T-15-07: forced local type + ignore-headers + trusted-package allow-list.
    assertThat(props.get(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS)).isEqualTo(false);
    assertThat(props.get(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE))
        .isEqualTo(OrderStockResultEvent.class.getName());
    assertThat(props.get(JacksonJsonDeserializer.TRUSTED_PACKAGES))
        .isEqualTo("com.example.feat1.DDD.order_context.application.event");
  }

  @Test
  void dltTemplateAndErrorHandlerAreProvided() {
    KafkaTemplate<String, Object> dlt = config.orderDltKafkaTemplate(BOOTSTRAP);
    assertThat(dlt).isNotNull();

    DefaultErrorHandler handler = config.orderStockResultErrorHandler(dlt);
    assertThat(handler).isNotNull();
  }

  @Test
  void listenerContainerFactoryUsesRecordAckMode() {
    ConsumerFactory<String, OrderStockResultEvent> cf =
        config.orderStockResultConsumerFactory(BOOTSTRAP, GROUP);
    DefaultErrorHandler handler =
        config.orderStockResultErrorHandler(config.orderDltKafkaTemplate(BOOTSTRAP));

    ConcurrentKafkaListenerContainerFactory<String, OrderStockResultEvent> factory =
        config.orderStockResultKafkaListenerContainerFactory(cf, handler);

    assertThat(factory).isNotNull();
    assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(AckMode.RECORD);
  }
}
