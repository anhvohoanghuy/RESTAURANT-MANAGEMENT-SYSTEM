package com.example.feat1.DDD.menu_context.infrastructure.repository;

import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import com.example.feat1.DDD.menu_context.infrastructure.entity.RecipeEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<RecipeEntity, UUID> {
  @EntityGraph(attributePaths = "lines")
  Optional<RecipeEntity> findByTargetTypeAndTargetId(RecipeTargetType targetType, UUID targetId);
}
