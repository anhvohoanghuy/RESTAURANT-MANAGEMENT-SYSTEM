package com.example.feat1.DDD.payment_context.infrastructure.repository;

import com.example.feat1.DDD.payment_context.infrastructure.entity.PaymentRefundEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefundEntity, UUID> {
  Optional<PaymentRefundEntity> findByPayment_IdAndIdempotencyKey(
      UUID paymentId, String idempotencyKey);

  List<PaymentRefundEntity> findByPayment_OrderId(UUID orderId);
}
