package com.example.feat1.DDD.order_context;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.feat1.DDD.order_context.application.event.OrderCreatedEvent;
import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

/**
 * Exercises the real spring-kafka Jackson-3 JacksonJsonSerializer -> JacksonJsonDeserializer path
 * the producers use, closing RESEARCH Pitfall 5 / assumption A2: the saga event contracts must
 * survive a genuine serde round-trip (records + Instant + BigDecimal + nested records/enums)
 * without a broker.
 *
 * <p>The project runs on Spring Boot 4 / Jackson 3 (tools.jackson), whose serde has native
 * java.time support. The producers therefore use the Jackson-3 {@code JacksonJsonSerializer}; the
 * legacy Jackson-2 {@code JsonSerializer} cannot serialize {@link Instant} on this classpath (no
 * jackson-datatype-jsr310 present), which is exactly the untested-serializer gap this test guards.
 */
class EventSerdeRoundTripTest {

  private static final String TOPIC = "serde-roundtrip";

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
  void orderCreatedEventSurvivesRoundTrip() {
    OrderCreatedEvent event =
        new OrderCreatedEvent(
            UUID.randomUUID(),
            OrderCreatedEvent.TYPE,
            Instant.parse("2026-07-07T10:15:30Z"),
            UUID.randomUUID(),
            UUID.randomUUID(),
            new OrderCreatedEvent.OrderTable(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SUB-01",
                "Table One",
                UUID.randomUUID(),
                "Main Area"),
            List.of(
                new OrderCreatedEvent.OrderLine(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Pho",
                    new BigDecimal("65000.00"),
                    List.of(
                        new OrderCreatedEvent.OrderTopping(
                            UUID.randomUUID(),
                            "Extras",
                            UUID.randomUUID(),
                            "Extra Beef",
                            new BigDecimal("5000.00"))),
                    new BigDecimal("5000.00"),
                    new BigDecimal("70000.00"),
                    2,
                    new BigDecimal("140000.00"))),
            new BigDecimal("140000.00"),
            Instant.parse("2026-07-07T10:15:30Z"));

    OrderCreatedEvent restored = roundTrip(event, OrderCreatedEvent.class);

    assertThat(restored).isEqualTo(event);
  }

  @Test
  void orderStockConfirmedEventSurvivesRoundTrip() {
    OrderStockResultEvent event =
        new OrderStockResultEvent(
            UUID.randomUUID(),
            OrderStockResultEvent.CONFIRMED_TYPE,
            Instant.parse("2026-07-07T10:16:00Z"),
            UUID.randomUUID(),
            OrderStockResultEvent.Result.CONFIRMED,
            List.of());

    OrderStockResultEvent restored = roundTrip(event, OrderStockResultEvent.class);

    assertThat(restored).isEqualTo(event);
    assertThat(restored.result()).isEqualTo(OrderStockResultEvent.Result.CONFIRMED);
    assertThat(restored.shortfalls()).isEmpty();
  }

  @Test
  void orderStockRejectedEventWithShortfallSurvivesRoundTrip() {
    OrderStockResultEvent event =
        new OrderStockResultEvent(
            UUID.randomUUID(),
            OrderStockResultEvent.REJECTED_TYPE,
            Instant.parse("2026-07-07T10:17:00Z"),
            UUID.randomUUID(),
            OrderStockResultEvent.Result.REJECTED,
            List.of(
                new OrderStockResultEvent.Shortfall(
                    UUID.randomUUID(), "Beef", new BigDecimal("3.000"), new BigDecimal("1.500"))));

    OrderStockResultEvent restored = roundTrip(event, OrderStockResultEvent.class);

    assertThat(restored).isEqualTo(event);
    assertThat(restored.result()).isEqualTo(OrderStockResultEvent.Result.REJECTED);
    assertThat(restored.shortfalls()).hasSize(1);
    assertThat(restored.shortfalls().get(0).ingredientName()).isEqualTo("Beef");
  }
}
