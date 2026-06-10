package com.example.feat1.DDD.menu_context.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public class RecipeLine {
  private final UUID id;
  private final String ingredient;
  private final BigDecimal quantity;
  private final String unit;
  private final int sortOrder;

  public RecipeLine(UUID id, String ingredient, BigDecimal quantity, String unit, int sortOrder) {
    if (ingredient == null || ingredient.isBlank()) {
      throw new IllegalArgumentException("Recipe line ingredient is required");
    }
    if (quantity == null || quantity.signum() <= 0) {
      throw new IllegalArgumentException("Recipe line quantity must be positive");
    }
    if (unit == null || unit.isBlank()) {
      throw new IllegalArgumentException("Recipe line unit is required");
    }
    this.id = id;
    this.ingredient = ingredient;
    this.quantity = quantity;
    this.unit = unit;
    this.sortOrder = sortOrder;
  }

  public UUID getId() {
    return id;
  }

  public String getIngredient() {
    return ingredient;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public String getUnit() {
    return unit;
  }

  public int getSortOrder() {
    return sortOrder;
  }
}
