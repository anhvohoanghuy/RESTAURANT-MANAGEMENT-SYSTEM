package com.example.feat1.DDD.inventory_context.infrastructure.config;

import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

/**
 * Producer wiring for the Inventory context's saga-result event ({@link OrderStockResultEvent}),
 * emitted on {@code inventory.order-stock-results} after a reservation is confirmed or rejected
 * (D-05). Structural copy of {@code OrderKafkaProducerConfig}, retyped to {@code
 * OrderStockResultEvent}.
 *
 * <p>Uses the phase-wide Jackson-3 {@link JacksonJsonSerializer} (established in 15-01) rather than
 * the legacy Jackson-2 {@code JsonSerializer}: the result event carries a {@code java.time.Instant}
 * ({@code occurredAt}) which the Jackson-2 serializer cannot serialize on this Boot 4 / Jackson 3
 * classpath (no {@code jackson-datatype-jsr310} present, and no new dependencies allowed).
 */
@Configuration
public class InventoryKafkaProducerConfig {
  @Bean
  public ProducerFactory<String, OrderStockResultEvent> orderStockResultProducerFactory(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
    return new DefaultKafkaProducerFactory<>(config);
  }

  @Bean
  public KafkaTemplate<String, OrderStockResultEvent> orderStockResultKafkaTemplate(
      ProducerFactory<String, OrderStockResultEvent> orderStockResultProducerFactory) {
    return new KafkaTemplate<>(orderStockResultProducerFactory);
  }
}
