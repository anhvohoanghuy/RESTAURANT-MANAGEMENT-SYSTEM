package com.example.feat1.DDD.kitchen_context.domain.port;

import com.example.feat1.DDD.inventory_context.application.event.SettleTriggerEvent;

/**
 * Publishes the EXISTING inventory {@link SettleTriggerEvent} contract (imported, never redeclared)
 * to the Phase-16 settlement topic, keyed by orderId, so Inventory can settle the held reservation
 * into an actual deduction once a kitchen item first enters PREPARING (D-03).
 */
public interface KitchenSettleTriggerPublisher {
  void publishSettleTrigger(SettleTriggerEvent event);
}
