package com.example.feat1.DDD.kitchen_context.application.event;

import com.example.feat1.DDD.kitchen_context.domain.model.KitchenItemStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbound event published on every advance of a kitchen ticket item. Carries the FULL per-item
 * status snapshot for the ticket (not just the item that changed) so {@code order_context} can
 * derive the order's aggregate fulfillment status purely from this one event, with no cross-context
 * lookup back into the kitchen context (RESEARCH Open Question #1).
 */
public record KitchenTicketStatusChangedEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    UUID orderId,
    UUID ticketId,
    List<ItemStatus> items) {
  public static final String TYPE = "KitchenTicketStatusChanged";

  public record ItemStatus(UUID orderLineId, KitchenItemStatus status) {}
}
