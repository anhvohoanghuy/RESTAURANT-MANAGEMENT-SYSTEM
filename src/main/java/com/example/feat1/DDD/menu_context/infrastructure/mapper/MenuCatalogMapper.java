package com.example.feat1.DDD.menu_context.infrastructure.mapper;

import com.example.feat1.DDD.menu_context.domain.model.Dish;
import com.example.feat1.DDD.menu_context.domain.model.MenuCategory;
import com.example.feat1.DDD.menu_context.domain.model.Recipe;
import com.example.feat1.DDD.menu_context.domain.model.RecipeLine;
import com.example.feat1.DDD.menu_context.domain.model.ToppingGroup;
import com.example.feat1.DDD.menu_context.domain.model.ToppingOption;
import com.example.feat1.DDD.menu_context.infrastructure.entity.DishEntity;
import com.example.feat1.DDD.menu_context.infrastructure.entity.MenuCategoryEntity;
import com.example.feat1.DDD.menu_context.infrastructure.entity.RecipeEntity;
import com.example.feat1.DDD.menu_context.infrastructure.entity.RecipeLineEntity;
import com.example.feat1.DDD.menu_context.infrastructure.entity.ToppingGroupEntity;
import com.example.feat1.DDD.menu_context.infrastructure.entity.ToppingOptionEntity;

public final class MenuCatalogMapper {
  private MenuCatalogMapper() {}

  public static MenuCategory toDomain(MenuCategoryEntity entity) {
    return new MenuCategory(
        entity.getId(),
        entity.getName(),
        entity.getDescription(),
        entity.getSortOrder(),
        entity.getStatus());
  }

  public static Dish toDomain(DishEntity entity) {
    return new Dish(
        entity.getId(),
        entity.getCategory().getId(),
        entity.getName(),
        entity.getDescription(),
        entity.getBasePrice(),
        entity.getStatus(),
        entity.getSortOrder());
  }

  public static ToppingGroup toDomain(ToppingGroupEntity entity) {
    return new ToppingGroup(
        entity.getId(),
        entity.getDish().getId(),
        entity.getName(),
        entity.getMinSelections(),
        entity.getMaxSelections(),
        entity.getSortOrder());
  }

  public static ToppingOption toDomain(ToppingOptionEntity entity) {
    return new ToppingOption(
        entity.getId(),
        entity.getToppingGroup().getId(),
        entity.getName(),
        entity.getAdditionalPrice(),
        entity.getStatus(),
        entity.getSortOrder());
  }

  public static Recipe toDomain(RecipeEntity entity) {
    return new Recipe(
        entity.getId(),
        entity.getTargetType(),
        entity.getTargetId(),
        entity.getName(),
        entity.getLines().stream()
            .map(
                line ->
                    new RecipeLine(
                        line.getId(),
                        line.getIngredientId(),
                        line.getIngredient(),
                        line.getQuantity(),
                        line.getUnit(),
                        line.getSortOrder()))
            .toList());
  }

  public static RecipeLineEntity toEntity(RecipeLine line, RecipeEntity recipe) {
    RecipeLineEntity entity = new RecipeLineEntity();
    entity.setId(line.getId());
    entity.setRecipe(recipe);
    entity.setIngredientId(line.getIngredientId());
    entity.setIngredient(line.getIngredient());
    entity.setQuantity(line.getQuantity());
    entity.setUnit(line.getUnit());
    entity.setSortOrder(line.getSortOrder());
    return entity;
  }
}
