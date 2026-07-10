package com.example.feat1.DDD.order_context.application.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbox payload published when an order or a subset of its lines is cancelled. Consumed
 * cross-context by Inventory (release the held reservation for the cancelled lines / recompute the
 * completion guard), Payment (auto-refund when {@code wholeOrder} is true, D-6), and Kitchen (void
 * the corresponding ticket items).
 */
public record OrderCancelledEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    UUID orderId,
    boolean wholeOrder,
    List<UUID> cancelledLineIds,
    int totalLines) {
  public static final String TYPE = "OrderCancelled";
}
