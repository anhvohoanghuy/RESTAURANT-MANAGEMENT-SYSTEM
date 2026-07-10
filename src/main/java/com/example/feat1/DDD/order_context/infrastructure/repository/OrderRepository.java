package com.example.feat1.DDD.order_context.infrastructure.repository;

import com.example.feat1.DDD.order_context.infrastructure.entity.OrderEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
  List<OrderEntity> findByUserIdOrderBySubmittedAtDesc(UUID userId);

  Optional<OrderEntity> findByIdAndUserId(UUID id, UUID userId);

  /**
   * Acquires a pessimistic write lock on the order row so a concurrent cancel/confirm/settle cannot
   * race with a cancellation (plan 18-02).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from OrderEntity o where o.id = :id")
  Optional<OrderEntity> lockById(UUID id);
}
