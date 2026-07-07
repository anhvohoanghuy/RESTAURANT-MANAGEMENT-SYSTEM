package com.example.feat1.DDD.inventory_context.infrastructure.repository;

import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryStockBalanceEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InventoryStockBalanceRepository
    extends JpaRepository<InventoryStockBalanceEntity, UUID> {

  Optional<InventoryStockBalanceEntity> findByIngredient_IdAndLocationCode(
      UUID ingredientId, String locationCode);

  List<InventoryStockBalanceEntity> findAllByOrderByIngredient_NameAsc();

  @Query(
      "select b from InventoryStockBalanceEntity b "
          + "where b.lowStockThreshold is not null and b.quantityOnHand <= b.lowStockThreshold "
          + "order by b.ingredient.name asc")
  List<InventoryStockBalanceEntity> findLowStock();
}
