package com.example.feat1.DDD.kitchen_context.domain.model;

/**
 * Per-item fulfillment lifecycle. Declaration order is semantically load-bearing: the forward-only,
 * single-step transition guard in plan 17-05 relies on this enum's ordinal ordering (QUEUED ->
 * PREPARING -> READY -> SERVED -> COMPLETED) to reject skipped or reverted transitions. Do not
 * reorder these values.
 *
 * <p>{@code CANCELLED} was appended LAST (plan 18-06, D-7): a terminal void state for a cancelled
 * order line's still-QUEUED kitchen item. It is placed beyond {@code COMPLETED} so it never
 * interferes with the forward lifecycle ordering above; {@link
 * com.example.feat1.DDD.kitchen_context.application.KitchenTicketAdvanceService}'s transition guard
 * is a {@code switch} (not ordinal-based), and explicitly rejects any advance FROM {@code
 * CANCELLED}.
 */
public enum KitchenItemStatus {
  QUEUED,
  PREPARING,
  READY,
  SERVED,
  COMPLETED,
  CANCELLED
}
