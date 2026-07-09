package com.example.feat1.DDD.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.feat1.DDD.payment_context.application.event.PaymentEvent;
import com.example.feat1.DDD.payment_context.infrastructure.config.PaymentKafkaProducerConfig;
import com.example.feat1.DDD.table_context.application.event.TableOperationEvent;
import com.example.feat1.DDD.table_context.infrastructure.config.TableKafkaProducerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

/**
 * Broker-free wiring guard for Payment/Table producer factories. These factories are explicit maps,
 * so they must stay aligned with the project's Jackson-3 producer serializer.
 */
class PaymentTableKafkaProducerConfigTest {

  private static final String BOOTSTRAP = "localhost:9092";

  private final PaymentKafkaProducerConfig paymentConfig = new PaymentKafkaProducerConfig();
  private final TableKafkaProducerConfig tableConfig = new TableKafkaProducerConfig();

  @Test
  void paymentProducerFactoryUsesJacksonJsonSerializerAndStringKeySerializer() {
    ProducerFactory<String, PaymentEvent> pf = paymentConfig.paymentEventProducerFactory(BOOTSTRAP);

    assertThat(pf).isNotNull();
    var props = pf.getConfigurationProperties();
    assertThat(props.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))
        .isEqualTo(StringSerializer.class);
    assertThat(props.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG))
        .isEqualTo(JacksonJsonSerializer.class);
  }

  @Test
  void paymentKafkaTemplateIsWired() {
    ProducerFactory<String, PaymentEvent> pf = paymentConfig.paymentEventProducerFactory(BOOTSTRAP);
    KafkaTemplate<String, PaymentEvent> template = paymentConfig.paymentEventKafkaTemplate(pf);

    assertThat(template).isNotNull();
  }

  @Test
  void tableProducerFactoryUsesJacksonJsonSerializerAndStringKeySerializer() {
    ProducerFactory<String, TableOperationEvent> pf =
        tableConfig.tableOperationEventProducerFactory(BOOTSTRAP);

    assertThat(pf).isNotNull();
    var props = pf.getConfigurationProperties();
    assertThat(props.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))
        .isEqualTo(StringSerializer.class);
    assertThat(props.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG))
        .isEqualTo(JacksonJsonSerializer.class);
  }

  @Test
  void tableKafkaTemplateIsWired() {
    ProducerFactory<String, TableOperationEvent> pf =
        tableConfig.tableOperationEventProducerFactory(BOOTSTRAP);
    KafkaTemplate<String, TableOperationEvent> template =
        tableConfig.tableOperationEventKafkaTemplate(pf);

    assertThat(template).isNotNull();
  }
}
