package com.example.feat1.DDD.menu_context.infrastructure.adapter;

import com.example.feat1.DDD.inventory_context.domain.port.MenuRecipeCostingPort;
import com.example.feat1.DDD.inventory_context.domain.snapshot.MenuCostingDishSnapshot;
import com.example.feat1.DDD.inventory_context.domain.snapshot.RecipeCostingSnapshot;
import com.example.feat1.DDD.menu_context.domain.model.MenuStatus;
import com.example.feat1.DDD.menu_context.domain.model.RecipeLine;
import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import com.example.feat1.DDD.menu_context.domain.repository.RecipeDomainRepository;
import com.example.feat1.DDD.menu_context.infrastructure.entity.DishEntity;
import com.example.feat1.DDD.menu_context.infrastructure.repository.DishRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MenuRecipeCostingAdapter implements MenuRecipeCostingPort {
  private final RecipeDomainRepository recipeRepository;
  private final DishRepository dishRepository;

  @Override
  @Transactional(readOnly = true)
  public Optional<RecipeCostingSnapshot> findRecipe(RecipeTargetType targetType, UUID targetId) {
    return recipeRepository
        .findByTarget(targetType, targetId)
        .map(
            recipe ->
                new RecipeCostingSnapshot(
                    recipe.getTargetType(),
                    recipe.getTargetId(),
                    recipe.getName(),
                    recipe.getLines().stream().map(this::toLine).toList()));
  }

  @Override
  @Transactional(readOnly = true)
  public List<MenuCostingDishSnapshot> listActiveDishes() {
    return dishRepository.findByStatusOrderBySortOrderAscNameAsc(MenuStatus.ACTIVE).stream()
        .map(this::toDish)
        .toList();
  }

  private RecipeCostingSnapshot.Line toLine(RecipeLine line) {
    return new RecipeCostingSnapshot.Line(
        line.getId(),
        line.getIngredientId(),
        line.getIngredient(),
        line.getQuantity(),
        line.getUnit(),
        line.getSortOrder());
  }

  private MenuCostingDishSnapshot toDish(DishEntity dish) {
    return new MenuCostingDishSnapshot(
        dish.getId(), dish.getName(), dish.getBasePrice(), dish.getCategory().getId());
  }
}
