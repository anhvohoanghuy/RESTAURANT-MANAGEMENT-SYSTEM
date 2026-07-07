package com.example.feat1.DDD.inventory_context.domain.port;

import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent;

/**
 * Outbound port for the Inventory context to publish the order-confirmation saga result ({@link
 * OrderStockResultEvent}) after a reservation is confirmed or rejected. Mirrors the one-method
 * shape of the order-side {@code OrderEventPublisher}; the Kafka adapter is wired in a later plan
 * (15-05).
 */
public interface InventoryStockResultPublisher {
  void publishStockResult(OrderStockResultEvent event);
}
