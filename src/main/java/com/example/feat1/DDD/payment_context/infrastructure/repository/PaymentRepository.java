package com.example.feat1.DDD.payment_context.infrastructure.repository;

import com.example.feat1.DDD.payment_context.infrastructure.entity.PaymentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PaymentRepository
    extends JpaRepository<PaymentEntity, UUID>, JpaSpecificationExecutor<PaymentEntity> {
  Optional<PaymentEntity> findByOrderIdAndIdempotencyKey(UUID orderId, String idempotencyKey);

  List<PaymentEntity> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
