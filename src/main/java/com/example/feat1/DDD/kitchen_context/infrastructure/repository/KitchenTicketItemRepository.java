package com.example.feat1.DDD.kitchen_context.infrastructure.repository;

import com.example.feat1.DDD.kitchen_context.domain.model.KitchenItemStatus;
import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenTicketItemEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface KitchenTicketItemRepository extends JpaRepository<KitchenTicketItemEntity, UUID> {

  /**
   * Acquires a pessimistic write lock on the item row keyed by BOTH the order id and item id. The
   * dual-key predicate serves two purposes at once: it serializes concurrent advances of the same
   * item so two transactions can never both read QUEUED (closing the double-settle-trigger race),
   * and it enforces that the item must belong to a ticket for the given orderId — closing the IDOR
   * threat of reaching another order's item through the URL path (mirrors OrderLineLookupAdapter's
   * dual-key lookup pattern).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from KitchenTicketItemEntity i where i.id = :id and i.ticket.orderId = :orderId")
  Optional<KitchenTicketItemEntity> lockByOrderIdAndItemId(UUID orderId, UUID id);

  List<KitchenTicketItemEntity> findByStatusNot(KitchenItemStatus status);
}
