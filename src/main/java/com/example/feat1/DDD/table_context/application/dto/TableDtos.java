package com.example.feat1.DDD.table_context.application.dto;

import com.example.feat1.DDD.table_context.domain.model.TableStatus;
import java.util.List;
import java.util.UUID;

public final class TableDtos {
  private TableDtos() {}

  public record DiningAreaRequest(String name, Integer sortOrder, TableStatus status) {}

  public record DiningTableRequest(
      UUID areaId,
      String code,
      String name,
      Integer capacity,
      Integer sortOrder,
      TableStatus status) {}

  public record DiningAreaResponse(UUID id, String name, int sortOrder, TableStatus status) {}

  public record DiningTableResponse(
      UUID id,
      UUID areaId,
      String areaName,
      String code,
      String name,
      Integer capacity,
      int sortOrder,
      TableStatus status) {}

  public record PublicTablesResponse(List<PublicDiningArea> areas) {}

  public record PublicDiningArea(
      UUID id, String name, int sortOrder, List<PublicDiningTable> tables) {}

  public record PublicDiningTable(
      UUID id, String code, String name, Integer capacity, int sortOrder) {}

  public record TableSnapshot(
      UUID tableId, String code, String name, UUID areaId, String areaName) {}
}
