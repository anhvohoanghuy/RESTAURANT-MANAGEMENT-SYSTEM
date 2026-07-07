package com.example.feat1.DDD.inventory_context.infrastructure.repository;

import com.example.feat1.DDD.inventory_context.infrastructure.entity.StockReservationEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReservationRepository extends JpaRepository<StockReservationEntity, UUID> {

  boolean existsByOrderId(UUID orderId);

  Optional<StockReservationEntity> findByOrderId(UUID orderId);
}
