package com.example.feat1.DDD.table_context.domain.model;

import java.util.UUID;

public class DiningTable {
  private final UUID id;
  private final UUID areaId;
  private final String code;
  private final String name;
  private final Integer capacity;
  private final int sortOrder;
  private final TableStatus status;

  public DiningTable(
      UUID id,
      UUID areaId,
      String code,
      String name,
      Integer capacity,
      int sortOrder,
      TableStatus status) {
    if (areaId == null) {
      throw new IllegalArgumentException("Dining table area is required");
    }
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Dining table code is required");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Dining table name is required");
    }
    if (capacity != null && capacity <= 0) {
      throw new IllegalArgumentException("Dining table capacity must be positive");
    }
    this.id = id;
    this.areaId = areaId;
    this.code = code.trim();
    this.name = name.trim();
    this.capacity = capacity;
    this.sortOrder = sortOrder;
    this.status = status == null ? TableStatus.ACTIVE : status;
  }

  public UUID getId() {
    return id;
  }

  public UUID getAreaId() {
    return areaId;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public Integer getCapacity() {
    return capacity;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public TableStatus getStatus() {
    return status;
  }
}
