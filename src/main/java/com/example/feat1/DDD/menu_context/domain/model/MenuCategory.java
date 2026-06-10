package com.example.feat1.DDD.menu_context.domain.model;

import java.util.UUID;

public class MenuCategory {
  private final UUID id;
  private final String name;
  private final String description;
  private final int sortOrder;
  private final MenuStatus status;

  public MenuCategory(UUID id, String name, String description, int sortOrder, MenuStatus status) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Category name is required");
    }
    this.id = id;
    this.name = name;
    this.description = description;
    this.sortOrder = sortOrder;
    this.status = status == null ? MenuStatus.ACTIVE : status;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public MenuStatus getStatus() {
    return status;
  }
}
