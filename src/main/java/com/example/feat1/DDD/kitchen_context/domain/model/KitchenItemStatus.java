package com.example.feat1.DDD.kitchen_context.domain.model;

/**
 * Per-item fulfillment lifecycle. Declaration order is semantically load-bearing: the forward-only,
 * single-step transition guard in plan 17-05 relies on this enum's ordinal ordering (QUEUED ->
 * PREPARING -> READY -> SERVED -> COMPLETED) to reject skipped or reverted transitions. Do not
 * reorder these values.
 */
public enum KitchenItemStatus {
  QUEUED,
  PREPARING,
  READY,
  SERVED,
  COMPLETED
}
