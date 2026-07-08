package com.example.feat1.DDD.order_context.infrastructure.repository;

import com.example.feat1.DDD.order_context.infrastructure.entity.OrderLineEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderLineRepository extends JpaRepository<OrderLineEntity, UUID> {
  Optional<OrderLineEntity> findByOrder_IdAndId(UUID orderId, UUID id);
}
