package com.example.feat1.DDD.payment_context.infrastructure.repository;

import com.example.feat1.DDD.payment_context.infrastructure.entity.PaymentProcessedEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentProcessedEventRepository
    extends JpaRepository<PaymentProcessedEventEntity, UUID> {

  boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);
}
