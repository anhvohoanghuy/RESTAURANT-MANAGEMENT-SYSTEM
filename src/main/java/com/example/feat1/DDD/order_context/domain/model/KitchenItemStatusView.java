package com.example.feat1.DDD.order_context.domain.model;

/**
 * Order-context-owned narrow view of a kitchen item's fulfillment status, returned by {@link
 * com.example.feat1.DDD.order_context.domain.port.KitchenItemStatusPort}. Deliberately collapses
 * kitchen_context's full {@code KitchenItemStatus} lifecycle (QUEUED, PREPARING, READY, SERVED,
 * COMPLETED) down to the single boundary order_context's cancellation flow actually needs — has
 * kitchen preparation started or not — so order_context never imports kitchen_context's internal
 * enum directly.
 */
public enum KitchenItemStatusView {
  BEFORE_PREPARING,
  AT_OR_AFTER_PREPARING;

  public boolean isBeforePreparing() {
    return this == BEFORE_PREPARING;
  }

  public boolean atOrAfterPreparing() {
    return this == AT_OR_AFTER_PREPARING;
  }
}
