package com.example.feat1.DDD.kitchen_context.infrastructure.repository;

import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenProcessedEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KitchenProcessedEventRepository
    extends JpaRepository<KitchenProcessedEventEntity, UUID> {

  boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);
}
