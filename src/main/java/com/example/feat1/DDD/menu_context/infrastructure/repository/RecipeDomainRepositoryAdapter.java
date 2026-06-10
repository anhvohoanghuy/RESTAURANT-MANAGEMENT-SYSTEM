package com.example.feat1.DDD.menu_context.infrastructure.repository;

import com.example.feat1.DDD.menu_context.domain.model.Recipe;
import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import com.example.feat1.DDD.menu_context.domain.repository.RecipeDomainRepository;
import com.example.feat1.DDD.menu_context.infrastructure.entity.RecipeEntity;
import com.example.feat1.DDD.menu_context.infrastructure.mapper.MenuCatalogMapper;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RecipeDomainRepositoryAdapter implements RecipeDomainRepository {
  private final RecipeRepository jpaRepository;

  @Override
  public Recipe save(Recipe recipe) {
    RecipeEntity entity =
        jpaRepository
            .findByTargetTypeAndTargetId(recipe.getTargetType(), recipe.getTargetId())
            .orElseGet(RecipeEntity::new);
    entity.setId(recipe.getId() == null ? entity.getId() : recipe.getId());
    entity.setTargetType(recipe.getTargetType());
    entity.setTargetId(recipe.getTargetId());
    entity.setName(recipe.getName());
    entity.getLines().clear();
    recipe
        .getLines()
        .forEach(line -> entity.getLines().add(MenuCatalogMapper.toEntity(line, entity)));
    return MenuCatalogMapper.toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<Recipe> findByTarget(RecipeTargetType targetType, UUID targetId) {
    return jpaRepository
        .findByTargetTypeAndTargetId(targetType, targetId)
        .map(MenuCatalogMapper::toDomain);
  }
}
