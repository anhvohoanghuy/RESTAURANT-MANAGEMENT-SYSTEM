package com.example.feat1.DDD.kitchen_context.domain.port;

import com.example.feat1.DDD.kitchen_context.application.event.KitchenTicketStatusChangedEvent;

/**
 * Publishes the full per-item status snapshot event so {@code order_context} can derive the
 * aggregate order fulfillment status (D-04).
 */
public interface KitchenTicketStatusChangedPublisher {
  void publishTicketStatusChanged(KitchenTicketStatusChangedEvent event);
}
