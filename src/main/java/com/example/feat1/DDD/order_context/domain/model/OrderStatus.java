package com.example.feat1.DDD.order_context.domain.model;

public enum OrderStatus {
  SUBMITTED,
  PENDING_CONFIRMATION,
  CONFIRMED,
  // The declaration order from CONFIRMED through COMPLETED below is semantically load-bearing:
  // a forward-only fulfillment rank guard (plan 17-07) depends on this exact ordering. Do not
  // reorder these values.
  PREPARING,
  READY,
  SERVED,
  COMPLETED,
  REJECTED
}
