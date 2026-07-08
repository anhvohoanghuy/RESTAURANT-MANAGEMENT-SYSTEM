package com.example.feat1.DDD.kitchen_context.infrastructure.config;

import com.example.feat1.DDD.kitchen_context.application.event.KitchenTicketStatusChangedEvent;
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
 * Producer wiring for the kitchen context's own {@link KitchenTicketStatusChangedEvent}. Structural
 * clone of {@code InventoryKafkaProducerConfig}, retyped to {@code
 * KitchenTicketStatusChangedEvent}.
 */
@Configuration
public class KitchenTicketStatusChangedProducerConfig {
  @Bean
  public ProducerFactory<String, KitchenTicketStatusChangedEvent>
      ticketStatusChangedProducerFactory(
          @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
    return new DefaultKafkaProducerFactory<>(config);
  }

  @Bean
  public KafkaTemplate<String, KitchenTicketStatusChangedEvent> ticketStatusChangedKafkaTemplate(
      ProducerFactory<String, KitchenTicketStatusChangedEvent> ticketStatusChangedProducerFactory) {
    return new KafkaTemplate<>(ticketStatusChangedProducerFactory);
  }
}
