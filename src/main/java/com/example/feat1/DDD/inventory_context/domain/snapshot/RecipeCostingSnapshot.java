package com.example.feat1.DDD.inventory_context.domain.snapshot;

import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RecipeCostingSnapshot(
    RecipeTargetType targetType, UUID targetId, String recipeName, List<Line> lines) {
  public record Line(
      UUID recipeLineId,
      UUID ingredientId,
      String ingredientName,
      BigDecimal quantity,
      String unit,
      int sortOrder) {}
}
