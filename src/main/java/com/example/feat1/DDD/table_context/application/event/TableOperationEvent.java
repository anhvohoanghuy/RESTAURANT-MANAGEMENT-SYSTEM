package com.example.feat1.DDD.table_context.application.event;

import java.time.Instant;
import java.util.UUID;

public record TableOperationEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    UUID tableId,
    UUID sessionId,
    UUID reservationId,
    UUID actorUserId,
    String state,
    String reason) {
  public static final String TABLE_SESSION_OPENED = "TableSessionOpened";
  public static final String TABLE_SESSION_CLOSED = "TableSessionClosed";
  public static final String TABLE_OCCUPANCY_CHANGED = "TableOccupancyChanged";
  public static final String RESERVATION_CREATED = "ReservationCreated";
  public static final String RESERVATION_STATUS_CHANGED = "ReservationStatusChanged";
}
