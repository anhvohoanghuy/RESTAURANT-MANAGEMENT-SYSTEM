package com.example.feat1.DDD.inventory_context.infrastructure.adapter;

import com.example.feat1.DDD.inventory_context.domain.port.InventoryStockResultPublisher;
import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka adapter implementing the Inventory outbound {@link InventoryStockResultPublisher} port:
 * publishes the order-confirmation saga result ({@link OrderStockResultEvent}) to {@code
 * inventory.order-stock-results}, keyed by {@code orderId} so all results for an order land on the
 * same partition (ordering). Mirrors the {@code KafkaOrderEventPublisher} adapter idiom (D-05).
 *
 * <p>Registered as a {@link Component} so component scanning wires it as the sole {@code
 * InventoryStockResultPublisher} bean, satisfying {@code InventoryReservationService}'s dependency
 * and unblocking every {@code @SpringBootTest} context load.
 */
@Component
@RequiredArgsConstructor
public class KafkaInventoryStockResultPublisher implements InventoryStockResultPublisher {
  private final KafkaTemplate<String, OrderStockResultEvent> orderStockResultKafkaTemplate;

  @Value("${inventory.events.order-stock-results-topic:inventory.order-stock-results}")
  private String topic;

  @Override
  public void publishStockResult(OrderStockResultEvent event) {
    orderStockResultKafkaTemplate.send(topic, event.orderId().toString(), event);
  }
}
