package com.example.feat1.DDD.inventory_context.infrastructure.repository;

import com.example.feat1.DDD.inventory_context.infrastructure.entity.StockReservationEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StockReservationRepository extends JpaRepository<StockReservationEntity, UUID> {

  boolean existsByOrderId(UUID orderId);

  Optional<StockReservationEntity> findByOrderId(UUID orderId);

  /**
   * Acquires a pessimistic write lock on the reservation row so concurrent settlements of the same
   * order are serialized and cannot double-settle a held reservation (D-04).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from StockReservationEntity r where r.orderId = :orderId")
  Optional<StockReservationEntity> lockByOrderId(UUID orderId);
}
