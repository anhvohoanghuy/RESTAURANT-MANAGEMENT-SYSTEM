package com.example.feat1.DDD.inventory_context.infrastructure.repository;

import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryProcessedEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryProcessedEventRepository
    extends JpaRepository<InventoryProcessedEventEntity, UUID> {

  boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);
}
