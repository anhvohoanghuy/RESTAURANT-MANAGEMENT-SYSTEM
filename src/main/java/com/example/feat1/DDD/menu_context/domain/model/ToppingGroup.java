package com.example.feat1.DDD.menu_context.domain.model;

import java.util.UUID;

public class ToppingGroup {
  private final UUID id;
  private final UUID dishId;
  private final String name;
  private final int minSelections;
  private final int maxSelections;
  private final int sortOrder;

  public ToppingGroup(
      UUID id, UUID dishId, String name, int minSelections, int maxSelections, int sortOrder) {
    if (dishId == null) {
      throw new IllegalArgumentException("Topping group dish is required");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Topping group name is required");
    }
    if (minSelections < 0 || maxSelections < 0 || minSelections > maxSelections) {
      throw new IllegalArgumentException("Topping group selections must satisfy 0 <= min <= max");
    }
    this.id = id;
    this.dishId = dishId;
    this.name = name;
    this.minSelections = minSelections;
    this.maxSelections = maxSelections;
    this.sortOrder = sortOrder;
  }

  public UUID getId() {
    return id;
  }

  public UUID getDishId() {
    return dishId;
  }

  public String getName() {
    return name;
  }

  public int getMinSelections() {
    return minSelections;
  }

  public int getMaxSelections() {
    return maxSelections;
  }

  public int getSortOrder() {
    return sortOrder;
  }
}
