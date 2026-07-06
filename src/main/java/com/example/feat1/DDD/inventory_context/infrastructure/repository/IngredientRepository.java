package com.example.feat1.DDD.inventory_context.infrastructure.repository;

import com.example.feat1.DDD.inventory_context.infrastructure.entity.IngredientEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientRepository extends JpaRepository<IngredientEntity, UUID> {
  List<IngredientEntity> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

  List<IngredientEntity> findAllByOrderByNameAsc();
}
