package com.example.feat1.DDD.menu_context.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class MenuSelectionDtos {
  private MenuSelectionDtos() {}

  public record MenuSelectionRequest(UUID dishId, List<UUID> toppingOptionIds) {}

  public record MenuSelectionQuote(
      UUID dishId,
      String dishName,
      BigDecimal basePrice,
      List<SelectedToppingSnapshot> selectedToppings,
      BigDecimal toppingsTotal,
      BigDecimal totalPrice) {}

  public record SelectedToppingSnapshot(
      UUID toppingGroupId,
      String toppingGroupName,
      UUID toppingOptionId,
      String toppingOptionName,
      BigDecimal additionalPrice) {}
}
