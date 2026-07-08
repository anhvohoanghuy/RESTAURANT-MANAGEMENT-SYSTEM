package com.example.feat1.DDD.kitchen_context.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.feat1.DDD.inventory_context.application.event.SettleTriggerEvent;
import com.example.feat1.DDD.kitchen_context.application.event.KitchenTicketStatusChangedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

/**
 * Broker-free wiring test for {@link KitchenSettleTriggerProducerConfig} and {@link
 * KitchenTicketStatusChangedProducerConfig}: instantiates both configs directly (no Spring context,
 * no live broker) and asserts each producer factory's key/value serializers and that the
 * KafkaTemplate beans are non-null, mirroring the broker-free config-test style used for consumer
 * wiring elsewhere in the codebase.
 */
class KitchenKafkaProducerConfigTest {

  private static final String BOOTSTRAP = "localhost:9092";

  private final KitchenSettleTriggerProducerConfig settleTriggerConfig =
      new KitchenSettleTriggerProducerConfig();
  private final KitchenTicketStatusChangedProducerConfig ticketStatusChangedConfig =
      new KitchenTicketStatusChangedProducerConfig();

  @Test
  void settleTriggerProducerFactoryUsesJacksonJsonSerializerAndStringKeySerializer() {
    ProducerFactory<String, SettleTriggerEvent> pf =
        settleTriggerConfig.settleTriggerProducerFactory(BOOTSTRAP);

    assertThat(pf).isNotNull();
    var props = pf.getConfigurationProperties();
    assertThat(props.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))
        .isEqualTo(StringSerializer.class);
    assertThat(props.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG))
        .isEqualTo(JacksonJsonSerializer.class);
  }

  @Test
  void settleTriggerKafkaTemplateIsWired() {
    ProducerFactory<String, SettleTriggerEvent> pf =
        settleTriggerConfig.settleTriggerProducerFactory(BOOTSTRAP);
    KafkaTemplate<String, SettleTriggerEvent> template =
        settleTriggerConfig.settleTriggerKafkaTemplate(pf);

    assertThat(template).isNotNull();
  }

  @Test
  void ticketStatusChangedProducerFactoryUsesJacksonJsonSerializerAndStringKeySerializer() {
    ProducerFactory<String, KitchenTicketStatusChangedEvent> pf =
        ticketStatusChangedConfig.ticketStatusChangedProducerFactory(BOOTSTRAP);

    assertThat(pf).isNotNull();
    var props = pf.getConfigurationProperties();
    assertThat(props.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))
        .isEqualTo(StringSerializer.class);
    assertThat(props.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG))
        .isEqualTo(JacksonJsonSerializer.class);
  }

  @Test
  void ticketStatusChangedKafkaTemplateIsWired() {
    ProducerFactory<String, KitchenTicketStatusChangedEvent> pf =
        ticketStatusChangedConfig.ticketStatusChangedProducerFactory(BOOTSTRAP);
    KafkaTemplate<String, KitchenTicketStatusChangedEvent> template =
        ticketStatusChangedConfig.ticketStatusChangedKafkaTemplate(pf);

    assertThat(template).isNotNull();
  }
}
