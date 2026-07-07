package com.example.feat1.DDD.order_context.infrastructure.repository;

import com.example.feat1.DDD.order_context.infrastructure.entity.OrderProcessedEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderProcessedEventRepository
    extends JpaRepository<OrderProcessedEventEntity, UUID> {

  boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);
}
