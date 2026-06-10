package com.example.feat1.DDD.menu_context.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public class Dish {
  private final UUID id;
  private final UUID categoryId;
  private final String name;
  private final String description;
  private final BigDecimal basePrice;
  private final MenuStatus status;
  private final int sortOrder;

  public Dish(
      UUID id,
      UUID categoryId,
      String name,
      String description,
      BigDecimal basePrice,
      MenuStatus status,
      int sortOrder) {
    if (categoryId == null) {
      throw new IllegalArgumentException("Dish category is required");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Dish name is required");
    }
    if (basePrice == null || basePrice.signum() < 0) {
      throw new IllegalArgumentException("Dish base price must be zero or positive");
    }
    this.id = id;
    this.categoryId = categoryId;
    this.name = name;
    this.description = description;
    this.basePrice = basePrice;
    this.status = status == null ? MenuStatus.ACTIVE : status;
    this.sortOrder = sortOrder;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getBasePrice() {
    return basePrice;
  }

  public MenuStatus getStatus() {
    return status;
  }

  public int getSortOrder() {
    return sortOrder;
  }
}
