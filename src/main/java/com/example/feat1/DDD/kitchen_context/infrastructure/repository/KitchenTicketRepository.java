package com.example.feat1.DDD.kitchen_context.infrastructure.repository;

import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenTicketEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KitchenTicketRepository extends JpaRepository<KitchenTicketEntity, UUID> {

  boolean existsByOrderId(UUID orderId);

  Optional<KitchenTicketEntity> findByOrderId(UUID orderId);
}
