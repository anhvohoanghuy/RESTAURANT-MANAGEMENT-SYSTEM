package com.example.feat1.DDD.kitchen_context.infrastructure.adapter;

import com.example.feat1.DDD.kitchen_context.domain.model.KitchenItemStatus;
import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenTicketItemEntity;
import com.example.feat1.DDD.kitchen_context.infrastructure.repository.KitchenTicketItemRepository;
import com.example.feat1.DDD.order_context.domain.model.KitchenItemStatusView;
import com.example.feat1.DDD.order_context.domain.port.KitchenItemStatusPort;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owning-context adapter for {@link KitchenItemStatusPort}. Reads every kitchen ticket item for an
 * order (non-locking) and maps each one's internal {@link KitchenItemStatus} down to the narrow,
 * order-context-owned {@link KitchenItemStatusView}, keyed by {@code orderLineId} — never exposing
 * the full {@code KitchenTicketItemEntity}/{@code KitchenTicketEntity} graph across the context
 * boundary.
 */
@Component
@RequiredArgsConstructor
public class KitchenItemStatusAdapter implements KitchenItemStatusPort {
  private final KitchenTicketItemRepository kitchenTicketItemRepository;

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, KitchenItemStatusView> findStatuses(UUID orderId) {
    return kitchenTicketItemRepository.findByTicket_OrderId(orderId).stream()
        .collect(
            Collectors.toMap(
                KitchenTicketItemEntity::getOrderLineId,
                item -> toView(item.getStatus()),
                (existing, duplicate) -> existing));
  }

  private KitchenItemStatusView toView(KitchenItemStatus status) {
    return status == KitchenItemStatus.QUEUED
        ? KitchenItemStatusView.BEFORE_PREPARING
        : KitchenItemStatusView.AT_OR_AFTER_PREPARING;
  }
}
