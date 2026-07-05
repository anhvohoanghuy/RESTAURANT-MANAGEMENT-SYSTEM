package com.example.feat1.DDD.order_context.infrastructure.repository;

import com.example.feat1.DDD.order_context.infrastructure.entity.OrderEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
  List<OrderEntity> findByUserIdOrderBySubmittedAtDesc(UUID userId);

  Optional<OrderEntity> findByIdAndUserId(UUID id, UUID userId);
}
