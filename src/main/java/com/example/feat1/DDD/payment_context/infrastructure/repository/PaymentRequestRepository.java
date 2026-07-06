package com.example.feat1.DDD.payment_context.infrastructure.repository;

import com.example.feat1.DDD.payment_context.infrastructure.entity.PaymentRequestEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequestEntity, UUID> {}
