package com.example.feat1.DDD.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.feat1.DDD.payment_context.application.event.PaymentEvent;
import com.example.feat1.DDD.payment_context.domain.model.PaymentMethod;
import com.example.feat1.DDD.table_context.application.event.TableOperationEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

/**
 * Exercises the real Spring Kafka Jackson-3 serializer/deserializer path for Payment/Table events
 * with Instant payloads. This catches regressions where dedicated producers drift back to the
 * legacy serializer path.
 */
class PaymentTableEventSerdeRoundTripTest {

  private static final String TOPIC = "payment-table-serde-roundtrip";

  private <T> T roundTrip(T value, Class<T> type) {
    try (JacksonJsonSerializer<T> serializer = new JacksonJsonSerializer<>();
        JacksonJsonDeserializer<T> deserializer = new JacksonJsonDeserializer<>(type)) {
      deserializer.addTrustedPackages("com.example.feat1.*");
      byte[] bytes = serializer.serialize(TOPIC, value);
      assertThat(bytes).isNotNull();
      return deserializer.deserialize(TOPIC, bytes);
    }
  }

  @Test
  void paymentEventWithInstantAndBigDecimalSurvivesRoundTrip() {
    Instant occurredAt = Instant.parse("2026-07-09T09:10:11Z");
    PaymentEvent event =
        new PaymentEvent(
            UUID.randomUUID(),
            PaymentEvent.PAYMENT_RECORDED,
            occurredAt,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            new BigDecimal("125000.50"),
            PaymentMethod.QR_CODE,
            UUID.randomUUID());

    PaymentEvent restored = roundTrip(event, PaymentEvent.class);

    assertThat(restored).isEqualTo(event);
    assertThat(restored.occurredAt()).isEqualTo(occurredAt);
    assertThat(restored.amount()).isEqualByComparingTo(new BigDecimal("125000.50"));
  }

  @Test
  void tableOperationEventWithInstantSurvivesRoundTrip() {
    Instant occurredAt = Instant.parse("2026-07-09T10:11:12Z");
    TableOperationEvent event =
        new TableOperationEvent(
            UUID.randomUUID(),
            TableOperationEvent.TABLE_SESSION_OPENED,
            occurredAt,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            "OCCUPIED",
            "walk-in");

    TableOperationEvent restored = roundTrip(event, TableOperationEvent.class);

    assertThat(restored).isEqualTo(event);
    assertThat(restored.occurredAt()).isEqualTo(occurredAt);
  }
}
