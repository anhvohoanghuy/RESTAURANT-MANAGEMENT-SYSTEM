package com.example.feat1.DDD.shared.outbox.infrastructure;

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
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the {@code @Scheduled} outbox relay poll loop (I-WR-02) and defines the {@code
 * String}-keyed/valued Kafka template the relay republishes pre-serialized outbox payloads through.
 * {@code @EnableScheduling} is added HERE ONLY — grep-verified absent everywhere else in {@code
 * src/main} — mirroring how {@code @EnableKafka} lives once per consumer config rather than being
 * duplicated app-wide.
 *
 * <p>Because every consumer in this codebase sets {@code USE_TYPE_INFO_HEADERS=false} and forces a
 * default value type, the wire payload is the JSON body only. A JSON string re-published via {@link
 * StringSerializer} is therefore byte-identical to a direct {@code JacksonJsonSerializer} send, so
 * the relay never needs to reconstruct a typed event or a typed template.
 */
@Configuration
@EnableScheduling
public class OutboxConfig {

  @Bean
  public ProducerFactory<String, String> outboxProducerFactory(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    return new DefaultKafkaProducerFactory<>(config);
  }

  @Bean
  public KafkaTemplate<String, String> outboxKafkaTemplate(
      ProducerFactory<String, String> outboxProducerFactory) {
    return new KafkaTemplate<>(outboxProducerFactory);
  }
}
