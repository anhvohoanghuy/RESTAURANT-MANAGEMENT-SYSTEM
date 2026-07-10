package com.example.feat1.DDD.inventory_context.infrastructure.repository;

import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryLineReleaseEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryLineReleaseRepository
    extends JpaRepository<InventoryLineReleaseEntity, UUID> {

  boolean existsByOrderIdAndOrderLineId(UUID orderId, UUID orderLineId);

  long countByOrderId(UUID orderId);
}
