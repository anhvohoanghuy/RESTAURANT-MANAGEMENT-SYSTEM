package com.example.feat1.DDD.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.feat1.DDD.order_context.application.event.OrderCreatedEvent;
import com.example.feat1.DDD.order_context.application.event.OrderCreatedEvent.OrderLine;
import com.example.feat1.DDD.order_context.application.event.OrderCreatedEvent.OrderTable;
import com.example.feat1.DDD.order_context.application.event.OrderCreatedEvent.OrderTopping;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import tools.jackson.databind.ObjectMapper;

/**
 * Closes WR-01: proves the transactional-outbox wire format round-trips losslessly through the REAL
 * production path — {@code OutboxWriter}'s injected {@link ObjectMapper} serializing the event,
 * exactly as {@code OutboxWriter.save} does, followed by a {@link JacksonJsonDeserializer}
 * configured IDENTICALLY to the production consumers ({@code
 * InventoryKafkaConsumerConfig#orderCreatedConsumerFactory} / {@code
 * OrderConfirmedKafkaConsumerConfig#orderConfirmedConsumerFactory}: {@code
 * USE_TYPE_INFO_HEADERS=false}, a forced {@code VALUE_DEFAULT_TYPE}, and the same {@code
 * TRUSTED_PACKAGES} allow-list).
 *
 * <p>{@link OrderCreatedEvent} is the representative event: it is the ONLY saga event that carries
 * BOTH a {@link Instant} field (occurredAt/submittedAt) AND {@link BigDecimal} fields (line pricing
 * + total), and it is exactly the event type the Inventory consumer forces via {@code
 * JacksonJsonDeserializer.VALUE_DEFAULT_TYPE}.
 *
 * <p>No embedded broker is used — the outbox relay is disabled in tests ({@code
 * outbox.relay.enabled=false}) and this test does not need Kafka at all: it exercises the
 * serializer and deserializer directly, which is exactly the boundary the relay crosses verbatim (a
 * JSON string republished byte-identically via {@code StringSerializer}, per {@code OutboxConfig}'s
 * class-level javadoc).
 */
@SpringBootTest
class OutboxWireFormatRoundTripTest {

  private static final String TRUSTED_PACKAGE =
      "com.example.feat1.DDD.order_context.application.event";

  // The exact ObjectMapper bean OutboxWriter has injected (Spring Boot's single autoconfigured
  // tools.jackson.databind.ObjectMapper — OutboxWriter declares no @Qualifier and no other
  // ObjectMapper @Bean exists in src/main, so this is byte-for-byte the same instance).
  @Autowired private ObjectMapper objectMapper;

  @Test
  void orderCreatedEventSurvivesOutboxWriterSerializeToConsumerDeserializeRoundTrip() {
    OrderCreatedEvent original = representativeOrderCreatedEvent();

    // Step 1: serialize EXACTLY as OutboxWriter.save does.
    String payload = objectMapper.writeValueAsString(original);

    // Step 2: deserialize with a JacksonJsonDeserializer configured identically to the production
    // consumer (InventoryKafkaConsumerConfig#orderCreatedConsumerFactory).
    OrderCreatedEvent roundTripped = deserializeAsProductionConsumerWould(payload);

    // Whole-object equality (record equals() compares every component, including nested records).
    assertThat(roundTripped).isEqualTo(original);

    // Explicit Instant field-equality (WR-01 acceptance: "Instant equal to the same instant").
    assertThat(roundTripped.occurredAt()).isEqualTo(original.occurredAt());
    assertThat(roundTripped.submittedAt()).isEqualTo(original.submittedAt());

    // Explicit BigDecimal field-equality: compareTo == 0 AND scale preserved (WR-01 acceptance).
    assertBigDecimalPreserved(roundTripped.total(), original.total());
    OrderLine originalLine = original.lines().get(0);
    OrderLine roundTrippedLine = roundTripped.lines().get(0);
    assertBigDecimalPreserved(roundTrippedLine.basePrice(), originalLine.basePrice());
    assertBigDecimalPreserved(roundTrippedLine.toppingsTotal(), originalLine.toppingsTotal());
    assertBigDecimalPreserved(roundTrippedLine.unitPrice(), originalLine.unitPrice());
    assertBigDecimalPreserved(roundTrippedLine.lineTotal(), originalLine.lineTotal());
    assertBigDecimalPreserved(
        roundTrippedLine.selectedToppings().get(0).additionalPrice(),
        originalLine.selectedToppings().get(0).additionalPrice());
  }

  private static void assertBigDecimalPreserved(BigDecimal actual, BigDecimal expected) {
    assertThat(actual.compareTo(expected))
        .as("numeric value of %s vs %s", actual, expected)
        .isZero();
    assertThat(actual.scale())
        .as("scale of %s vs %s", actual, expected)
        .isEqualTo(expected.scale());
  }

  private static OrderCreatedEvent representativeOrderCreatedEvent() {
    UUID toppingGroupId = UUID.randomUUID();
    UUID toppingOptionId = UUID.randomUUID();
    OrderTopping topping =
        new OrderTopping(toppingGroupId, "Size", toppingOptionId, "Large", new BigDecimal("5.50"));

    UUID lineId = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    OrderLine line =
        new OrderLine(
            lineId,
            dishId,
            "Pho Bo",
            new BigDecimal("45.00"),
            List.of(topping),
            new BigDecimal("5.50"),
            new BigDecimal("50.50"),
            2,
            new BigDecimal("101.00"));

    OrderTable table =
        new OrderTable(
            UUID.randomUUID(), UUID.randomUUID(), "T01", "Table 1", UUID.randomUUID(), "Patio");

    return new OrderCreatedEvent(
        UUID.randomUUID(),
        OrderCreatedEvent.TYPE,
        Instant.now(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        table,
        List.of(line),
        new BigDecimal("101.00"),
        Instant.now());
  }

  /**
   * Mirrors {@code InventoryKafkaConsumerConfig#orderCreatedConsumerFactory} property-for-property.
   */
  private static OrderCreatedEvent deserializeAsProductionConsumerWould(String payload) {
    Map<String, Object> props = new HashMap<>();
    props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, TRUSTED_PACKAGE);
    props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, OrderCreatedEvent.class.getName());
    props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);

    try (JacksonJsonDeserializer<OrderCreatedEvent> deserializer =
        new JacksonJsonDeserializer<>()) {
      deserializer.configure(props, false);
      return deserializer.deserialize("orders.created", payload.getBytes(StandardCharsets.UTF_8));
    }
  }
}
