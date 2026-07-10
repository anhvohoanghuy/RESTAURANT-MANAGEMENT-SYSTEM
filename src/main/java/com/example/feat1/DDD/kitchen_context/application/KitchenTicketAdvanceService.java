package com.example.feat1.DDD.kitchen_context.application;

import com.example.feat1.DDD.inventory_context.application.event.SettleTriggerEvent;
import com.example.feat1.DDD.kitchen_context.application.dto.AdvanceItemStatusRequest;
import com.example.feat1.DDD.kitchen_context.application.dto.KitchenItemResponse;
import com.example.feat1.DDD.kitchen_context.application.event.KitchenTicketStatusChangedEvent;
import com.example.feat1.DDD.kitchen_context.application.event.KitchenTicketStatusChangedEvent.ItemStatus;
import com.example.feat1.DDD.kitchen_context.domain.model.KitchenDomainException;
import com.example.feat1.DDD.kitchen_context.domain.model.KitchenItemStatus;
import com.example.feat1.DDD.kitchen_context.domain.port.KitchenSettleTriggerPublisher;
import com.example.feat1.DDD.kitchen_context.domain.port.KitchenTicketStatusChangedPublisher;
import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenTicketEntity;
import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenTicketItemEntity;
import com.example.feat1.DDD.kitchen_context.infrastructure.repository.KitchenTicketItemRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Highest-risk fulfillment use case of the phase: locks the item row, enforces the single-step
 * forward-only transition (D-02), and — after commit — publishes the {@link SettleTriggerEvent}
 * exactly once on {@code QUEUED->PREPARING} (D-03) plus the {@link KitchenTicketStatusChangedEvent}
 * snapshot on every valid transition.
 */
@Service
@RequiredArgsConstructor
public class KitchenTicketAdvanceService {

  private final KitchenTicketItemRepository itemRepository;
  private final KitchenSettleTriggerPublisher settleTriggerPublisher;
  private final KitchenTicketStatusChangedPublisher statusChangedPublisher;

  @Transactional
  public KitchenItemResponse advance(
      UUID orderId, UUID itemId, AdvanceItemStatusRequest request, UUID actorId) {
    // Lock BEFORE the forward-only status check (closes the double-settle-trigger race, Pitfall
    // 1 / T-17-10): a concurrent advance on the same item is serialized here, so a second
    // transaction only ever observes the already-mutated status.
    KitchenTicketItemEntity item =
        itemRepository
            .lockByOrderIdAndItemId(orderId, itemId)
            .orElseThrow(KitchenDomainException::itemNotFound);

    KitchenItemStatus current = item.getStatus();
    KitchenItemStatus target = request == null ? null : request.targetStatus();
    if (!isValidTransition(current, target)) {
      throw KitchenDomainException.transitionInvalid();
    }

    item.setStatus(target);
    item.setAdvancedBy(actorId);
    item.setAdvancedAt(Instant.now());
    KitchenTicketItemEntity saved = itemRepository.save(item);

    if (current == KitchenItemStatus.QUEUED && target == KitchenItemStatus.PREPARING) {
      publishSettleTriggerAfterCommit(
          new SettleTriggerEvent(
              UUID.randomUUID(),
              SettleTriggerEvent.TYPE,
              Instant.now(),
              orderId,
              saved.getOrderLineId(),
              saved.getTicket().getItems().size()));
    }

    publishStatusChangedAfterCommit(toStatusChangedEvent(saved));

    return toItemResponse(orderId, saved);
  }

  private boolean isValidTransition(KitchenItemStatus current, KitchenItemStatus target) {
    if (current == null || target == null) {
      return false;
    }
    return switch (current) {
      case QUEUED -> target == KitchenItemStatus.PREPARING;
      case PREPARING -> target == KitchenItemStatus.READY;
      case READY -> target == KitchenItemStatus.SERVED;
      case SERVED -> target == KitchenItemStatus.COMPLETED;
      case COMPLETED -> false;
        // A voided item is terminal: never advanceable (18-06 / D-7), closing the cancel-vs-advance
        // race that would otherwise fire a rogue SettleTrigger for a released line.
      case CANCELLED -> false;
    };
  }

  private KitchenTicketStatusChangedEvent toStatusChangedEvent(KitchenTicketItemEntity item) {
    KitchenTicketEntity ticket = item.getTicket();
    return new KitchenTicketStatusChangedEvent(
        UUID.randomUUID(),
        KitchenTicketStatusChangedEvent.TYPE,
        Instant.now(),
        ticket.getOrderId(),
        ticket.getId(),
        ticket.getItems().stream()
            .map(i -> new ItemStatus(i.getOrderLineId(), i.getStatus()))
            .toList());
  }

  private KitchenItemResponse toItemResponse(UUID orderId, KitchenTicketItemEntity item) {
    return new KitchenItemResponse(
        item.getId(),
        orderId,
        item.getOrderLineId(),
        item.getDishName(),
        item.getQuantity(),
        item.getStatus());
  }

  private void publishSettleTriggerAfterCommit(SettleTriggerEvent event) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      settleTriggerPublisher.publishSettleTrigger(event);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            settleTriggerPublisher.publishSettleTrigger(event);
          }
        });
  }

  private void publishStatusChangedAfterCommit(KitchenTicketStatusChangedEvent event) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      statusChangedPublisher.publishTicketStatusChanged(event);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            statusChangedPublisher.publishTicketStatusChanged(event);
          }
        });
  }
}
