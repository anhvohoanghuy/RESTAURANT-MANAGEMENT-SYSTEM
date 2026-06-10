package com.example.feat1.DDD.menu_context.application.dto;

import com.example.feat1.DDD.menu_context.domain.model.MenuStatus;
import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class MenuDtos {
  private MenuDtos() {}

  public record CategoryRequest(
      String name, String description, Integer sortOrder, MenuStatus status) {}

  public record DishRequest(
      UUID categoryId,
      String name,
      String description,
      BigDecimal basePrice,
      MenuStatus status,
      Integer sortOrder) {}

  public record ToppingGroupRequest(
      UUID dishId, String name, Integer minSelections, Integer maxSelections, Integer sortOrder) {}

  public record ToppingOptionRequest(
      UUID toppingGroupId,
      String name,
      BigDecimal additionalPrice,
      MenuStatus status,
      Integer sortOrder) {}

  public record RecipeRequest(
      RecipeTargetType targetType, UUID targetId, String name, List<Line> lines) {
    public record Line(String ingredient, BigDecimal quantity, String unit, Integer sortOrder) {}
  }

  public record CategoryResponse(
      UUID id, String name, String description, int sortOrder, MenuStatus status) {}

  public record DishResponse(
      UUID id,
      UUID categoryId,
      String name,
      String description,
      BigDecimal basePrice,
      MenuStatus status,
      int sortOrder) {}

  public record ToppingGroupResponse(
      UUID id, UUID dishId, String name, int minSelections, int maxSelections, int sortOrder) {}

  public record ToppingOptionResponse(
      UUID id,
      UUID toppingGroupId,
      String name,
      BigDecimal additionalPrice,
      MenuStatus status,
      int sortOrder) {}

  public record RecipeResponse(
      UUID id, RecipeTargetType targetType, UUID targetId, String name, List<Line> lines) {
    public record Line(
        UUID id, String ingredient, BigDecimal quantity, String unit, int sortOrder) {}
  }

  public record PublicMenuResponse(List<PublicCategory> categories) {}

  public record PublicCategory(
      UUID id, String name, String description, int sortOrder, List<PublicDish> dishes) {}

  public record PublicDish(
      UUID id,
      String name,
      String description,
      BigDecimal basePrice,
      int sortOrder,
      List<PublicToppingGroup> toppingGroups) {}

  public record PublicToppingGroup(
      UUID id,
      String name,
      int minSelections,
      int maxSelections,
      int sortOrder,
      List<PublicToppingOption> options) {}

  public record PublicToppingOption(
      UUID id, String name, BigDecimal additionalPrice, int sortOrder) {}
}
