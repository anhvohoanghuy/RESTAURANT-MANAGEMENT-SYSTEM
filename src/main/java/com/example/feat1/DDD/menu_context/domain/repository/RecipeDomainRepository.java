package com.example.feat1.DDD.menu_context.domain.repository;

import com.example.feat1.DDD.menu_context.domain.model.Recipe;
import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import java.util.Optional;
import java.util.UUID;

public interface RecipeDomainRepository {
  Recipe save(Recipe recipe);

  Optional<Recipe> findByTarget(RecipeTargetType targetType, UUID targetId);
}
