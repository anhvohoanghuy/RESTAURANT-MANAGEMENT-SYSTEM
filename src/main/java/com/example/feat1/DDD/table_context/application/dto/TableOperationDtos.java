package com.example.feat1.DDD.table_context.application.dto;

import com.example.feat1.DDD.table_context.domain.model.ReservationStatus;
import com.example.feat1.DDD.table_context.domain.model.TableOccupancyState;
import com.example.feat1.DDD.table_context.domain.model.TableSessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TableOperationDtos {
  private TableOperationDtos() {}

  public record OpenTableSessionRequest(Integer partySize, String note, UUID reservationId) {}

  public record CloseTableSessionRequest(TableOccupancyState nextState, String note) {}

  public record CancelTableSessionRequest(String reason) {}

  public record SetTableOccupancyRequest(TableOccupancyState state, String reason) {}

  public record CreateReservationRequest(
      UUID tableId,
      String customerName,
      String customerPhone,
      String customerEmail,
      Integer partySize,
      Instant startTime,
      Instant endTime,
      String note) {}

  public record UpdateReservationStatusRequest(ReservationStatus status, String note) {}

  public record TableSessionResponse(
      UUID sessionId,
      UUID tableId,
      String tableCode,
      String tableName,
      UUID areaId,
      String areaName,
      TableSessionStatus status,
      Integer partySize,
      String note,
      UUID reservationId,
      Instant openedAt,
      Instant closedAt,
      Instant cancelledAt) {}

  public record TableReservationResponse(
      UUID reservationId,
      UUID tableId,
      String tableCode,
      String tableName,
      UUID areaId,
      String areaName,
      String customerName,
      String customerPhone,
      String customerEmail,
      int partySize,
      Instant startTime,
      Instant endTime,
      ReservationStatus status,
      String note,
      Instant createdAt,
      Instant updatedAt) {}

  public record TableOccupancyResponse(
      UUID tableId,
      String tableCode,
      String tableName,
      UUID areaId,
      String areaName,
      Integer capacity,
      TableOccupancyState state,
      TableSessionResponse activeSession,
      TableReservationResponse activeReservation) {}

  public record TableAvailabilityResponse(Instant from, Instant to, List<AvailableTable> tables) {}

  public record AvailableTable(
      UUID tableId,
      String tableCode,
      String tableName,
      UUID areaId,
      String areaName,
      Integer capacity,
      TableOccupancyState state) {}
}
