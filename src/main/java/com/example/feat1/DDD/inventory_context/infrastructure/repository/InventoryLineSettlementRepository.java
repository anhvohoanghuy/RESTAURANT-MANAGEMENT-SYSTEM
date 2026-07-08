package com.example.feat1.DDD.inventory_context.infrastructure.repository;

import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryLineSettlementEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryLineSettlementRepository
    extends JpaRepository<InventoryLineSettlementEntity, UUID> {

  boolean existsByOrderIdAndOrderLineId(UUID orderId, UUID orderLineId);

  long countByOrderId(UUID orderId);
}
