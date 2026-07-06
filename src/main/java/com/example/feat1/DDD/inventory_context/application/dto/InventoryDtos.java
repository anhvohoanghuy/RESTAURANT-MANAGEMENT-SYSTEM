package com.example.feat1.DDD.inventory_context.application.dto;

import com.example.feat1.DDD.inventory_context.domain.model.IngredientStatus;
import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InventoryDtos {
  private InventoryDtos() {}

  public record IngredientRequest(
      String name, String baseUnit, String description, IngredientStatus status) {}

  public record IngredientResponse(
      UUID ingredientId,
      String name,
      String baseUnit,
      String description,
      IngredientStatus status,
      Instant createdAt,
      Instant updatedAt) {}

  public record IngredientCostRequest(
      BigDecimal unitCost, String costUnit, Instant effectiveAt, String source, String note) {}

  public record IngredientCostResponse(
      UUID costId,
      UUID ingredientId,
      BigDecimal unitCost,
      String costUnit,
      Instant effectiveAt,
      String source,
      String note,
      Instant createdAt) {}

  public record RecipeCostResponse(
      RecipeTargetType targetType,
      UUID targetId,
      String recipeName,
      BigDecimal totalCost,
      boolean fullyCosted,
      List<RecipeCostLineResponse> lines) {}

  public record RecipeCostLineResponse(
      UUID recipeLineId,
      UUID ingredientId,
      String ingredientName,
      BigDecimal quantity,
      String unit,
      BigDecimal convertedQuantity,
      String costUnit,
      BigDecimal unitCost,
      BigDecimal lineCost,
      boolean costed,
      String reason) {}

  public record MenuCostingResponse(List<MenuCostingItemResponse> items) {}

  public record MenuCostingItemResponse(
      UUID dishId,
      String dishName,
      BigDecimal sellPrice,
      BigDecimal estimatedCost,
      BigDecimal grossMarginAmount,
      BigDecimal grossMarginPercent,
      boolean fullyCosted,
      int uncostedLineCount) {}
}
