package com.example.feat1.DDD.order_context.infrastructure.repository;

import com.example.feat1.DDD.order_context.infrastructure.entity.OrderCartEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderCartRepository extends JpaRepository<OrderCartEntity, UUID> {
  Optional<OrderCartEntity> findByUserId(UUID userId);
}
