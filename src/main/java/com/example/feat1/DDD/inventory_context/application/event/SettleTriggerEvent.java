package com.example.feat1.DDD.inventory_context.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbound settle-trigger event consumed by the Inventory Context to settle a single order line into
 * an actual stock deduction. Carries only routing/identity data — no recipe or ingredient amounts:
 * inventory re-resolves the recipe itself (D-01). {@code totalLines} lets the settlement service
 * detect the last line of an order without inspecting per-ingredient reservation lines.
 */
public record SettleTriggerEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    UUID orderId,
    UUID orderLineId,
    int totalLines) {
  public static final String TYPE = "SettleTrigger";
}
