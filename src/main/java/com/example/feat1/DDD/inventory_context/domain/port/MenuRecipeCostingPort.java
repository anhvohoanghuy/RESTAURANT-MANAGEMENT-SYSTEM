package com.example.feat1.DDD.inventory_context.domain.port;

import com.example.feat1.DDD.inventory_context.domain.snapshot.MenuCostingDishSnapshot;
import com.example.feat1.DDD.inventory_context.domain.snapshot.RecipeCostingSnapshot;
import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuRecipeCostingPort {
  Optional<RecipeCostingSnapshot> findRecipe(RecipeTargetType targetType, UUID targetId);

  List<MenuCostingDishSnapshot> listActiveDishes();
}
