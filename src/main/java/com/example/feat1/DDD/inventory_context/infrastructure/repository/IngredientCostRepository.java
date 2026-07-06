package com.example.feat1.DDD.inventory_context.infrastructure.repository;

import com.example.feat1.DDD.inventory_context.infrastructure.entity.IngredientCostEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientCostRepository extends JpaRepository<IngredientCostEntity, UUID> {
  List<IngredientCostEntity> findByIngredient_IdOrderByEffectiveAtDesc(UUID ingredientId);

  Optional<IngredientCostEntity>
      findTopByIngredient_IdAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
          UUID ingredientId, Instant effectiveAt);
}
