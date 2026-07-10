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

  /**
   * Acquires a pessimistic write lock on the item row keyed by the order id and its {@code
   * orderLineId} (plan 18-06, D-7). Used by {@code KitchenTicketInvalidationService} to serialize a
   * cancel-triggered void against a concurrent staff advance of the same line's item, closing the
   * cancel-vs-advance race — mirrors {@link #lockByOrderIdAndItemId}'s dual-key shape but keys on
   * {@code orderLineId} since the inbound {@code OrderCancelledEvent} carries line ids, not kitchen
   * item ids.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select i from KitchenTicketItemEntity i where i.ticket.orderId = :orderId and"
          + " i.orderLineId = :orderLineId")
  Optional<KitchenTicketItemEntity> lockByOrderIdAndOrderLineId(UUID orderId, UUID orderLineId);

  List<KitchenTicketItemEntity> findByStatusNot(KitchenItemStatus status);

  /**
   * Non-locking read of every kitchen item across all tickets for an order, keyed indirectly by
   * {@code orderLineId} on each returned entity. Used by {@link
   * com.example.feat1.DDD.kitchen_context.infrastructure.adapter.KitchenItemStatusAdapter} for the
   * order_context race-safe PREPARING guard (plan 18-02) — deliberately does NOT lock, since it is
   * a read-only cross-context snapshot taken inside the CALLER's already-locked order transaction.
   */
  List<KitchenTicketItemEntity> findByTicket_OrderId(UUID orderId);
}
