package com.example.feat1.DDD.order_context.infrastructure.repository;

import com.example.feat1.DDD.order_context.infrastructure.entity.OrderCartLineEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderCartLineRepository extends JpaRepository<OrderCartLineEntity, UUID> {
  List<OrderCartLineEntity> findByCart_IdOrderByIdAsc(UUID cartId);

  Optional<OrderCartLineEntity> findByCart_IdAndId(UUID cartId, UUID id);

  Optional<OrderCartLineEntity> findByCart_IdAndDishIdAndToppingKey(
      UUID cartId, UUID dishId, String toppingKey);

  long countByCart_Id(UUID cartId);

  void deleteByCart_Id(UUID cartId);
}
