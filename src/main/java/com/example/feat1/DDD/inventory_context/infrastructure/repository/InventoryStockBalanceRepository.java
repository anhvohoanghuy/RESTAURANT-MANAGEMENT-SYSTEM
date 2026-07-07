package com.example.feat1.DDD.inventory_context.infrastructure.repository;

import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryStockBalanceEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface InventoryStockBalanceRepository
    extends JpaRepository<InventoryStockBalanceEntity, UUID> {

  Optional<InventoryStockBalanceEntity> findByIngredient_IdAndLocationCode(
      UUID ingredientId, String locationCode);

  /**
   * Acquires a pessimistic write lock on the balance row so concurrent reservers are serialized and
   * cannot drive available stock negative (D-02).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select b from InventoryStockBalanceEntity b "
          + "where b.ingredient.id = :ingredientId and b.locationCode = :loc")
  Optional<InventoryStockBalanceEntity> lockByIngredientAndLocation(UUID ingredientId, String loc);

  List<InventoryStockBalanceEntity> findAllByOrderByIngredient_NameAsc();

  @Query(
      "select b from InventoryStockBalanceEntity b "
          + "where b.lowStockThreshold is not null and b.quantityOnHand <= b.lowStockThreshold "
          + "order by b.ingredient.name asc")
  List<InventoryStockBalanceEntity> findLowStock();
}
