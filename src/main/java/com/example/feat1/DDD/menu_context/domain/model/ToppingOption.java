package com.example.feat1.DDD.menu_context.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public class ToppingOption {
  private final UUID id;
  private final UUID toppingGroupId;
  private final String name;
  private final BigDecimal additionalPrice;
  private final MenuStatus status;
  private final int sortOrder;

  public ToppingOption(
      UUID id,
      UUID toppingGroupId,
      String name,
      BigDecimal additionalPrice,
      MenuStatus status,
      int sortOrder) {
    if (toppingGroupId == null) {
      throw new IllegalArgumentException("Topping option group is required");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Topping option name is required");
    }
    if (additionalPrice == null || additionalPrice.signum() < 0) {
      throw new IllegalArgumentException("Topping additional price must be zero or positive");
    }
    this.id = id;
    this.toppingGroupId = toppingGroupId;
    this.name = name;
    this.additionalPrice = additionalPrice;
    this.status = status == null ? MenuStatus.ACTIVE : status;
    this.sortOrder = sortOrder;
  }

  public UUID getId() {
    return id;
  }

  public UUID getToppingGroupId() {
    return toppingGroupId;
  }

  public String getName() {
    return name;
  }

  public BigDecimal getAdditionalPrice() {
    return additionalPrice;
  }

  public MenuStatus getStatus() {
    return status;
  }

  public int getSortOrder() {
    return sortOrder;
  }
}
