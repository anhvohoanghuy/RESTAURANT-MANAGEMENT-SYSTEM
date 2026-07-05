package com.example.feat1.DDD.table_context.domain.model;

import java.util.UUID;

public class DiningArea {
  private final UUID id;
  private final String name;
  private final int sortOrder;
  private final TableStatus status;

  public DiningArea(UUID id, String name, int sortOrder, TableStatus status) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Dining area name is required");
    }
    this.id = id;
    this.name = name.trim();
    this.sortOrder = sortOrder;
    this.status = status == null ? TableStatus.ACTIVE : status;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public TableStatus getStatus() {
    return status;
  }
}
