package com.example.feat1.DDD.inventory_context.infrastructure.repository;

import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryStockMovementEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryStockMovementRepository
    extends JpaRepository<InventoryStockMovementEntity, UUID> {

  List<InventoryStockMovementEntity> findByIngredient_IdOrderByCreatedAtDesc(UUID ingredientId);

  List<InventoryStockMovementEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

  List<InventoryStockMovementEntity> findByIngredient_IdOrderByCreatedAtDesc(
      UUID ingredientId, Pageable pageable);
}
