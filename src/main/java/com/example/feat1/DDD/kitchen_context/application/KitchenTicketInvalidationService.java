package com.example.feat1.DDD.kitchen_context.application;

import com.example.feat1.DDD.kitchen_context.domain.model.KitchenItemStatus;
import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenProcessedEventEntity;
import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenTicketItemEntity;
import com.example.feat1.DDD.kitchen_context.infrastructure.repository.KitchenProcessedEventRepository;
import com.example.feat1.DDD.kitchen_context.infrastructure.repository.KitchenTicketItemRepository;
import com.example.feat1.DDD.order_context.application.event.OrderCancelledEvent;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kitchen-side defensive backstop for CANCEL-08 / D-7: on each {@link OrderCancelledEvent}, voids
 * every cancelled line's kitchen ticket item that is STILL {@code QUEUED} so staff can no longer
 * advance it to {@code PREPARING} — which would otherwise fire a {@code SettleTrigger} deducting
 * stock already released back to Inventory by the cancellation's compensation flow.
 *
 * <p>Never touches an item already {@code >= PREPARING} (defense-in-depth, mirrors the
 * REJECTED/rank-guard idiom elsewhere in this codebase): a legitimately in-progress or completed
 * item must never be silently voided out from under staff.
 *
 * <p>Idempotent via the kitchen {@code processed_events} ledger (idiom 1, mirrors {@link
 * KitchenTicketCreationService}): the ledger row is recorded LAST in this same transaction, so a
 * concurrent-duplicate unique violation rolls back the WHOLE transaction — including any item voids
 * already applied in this pass — and Kafka redelivers instead of leaking a partially-voided ticket.
 */
@Service
@RequiredArgsConstructor
public class KitchenTicketInvalidationService {

  private static final Logger log = LoggerFactory.getLogger(KitchenTicketInvalidationService.class);

  /** Ledger consumer identity for the kitchen-side OrderCancelled handler. */
  static final String CONSUMER_NAME = "kitchen-order-cancelled";

  private final KitchenProcessedEventRepository processedEventRepository;
  private final KitchenTicketItemRepository itemRepository;

  @Transactional
  public void onOrderCancelled(OrderCancelledEvent event) {
    // (1) Idempotency fast pre-check: absorb an already-recorded replay cheaply without touching
    // any item row. The authoritative ledger insert happens at the END of this method, in this
    // same transaction (mirrors KitchenTicketCreationService's ledger-last-in-tx idiom).
    if (processedEventRepository.existsByEventIdAndConsumerName(event.eventId(), CONSUMER_NAME)) {
      log.debug(
          "Skipping already-processed OrderCancelled eventId={} orderId={}",
          event.eventId(),
          event.orderId());
      return;
    }

    // (2) For each cancelled line: lock the item row (serializes against a concurrent staff
    // advance of the SAME item, closing the cancel-vs-advance race), then ONLY void it if it is
    // still QUEUED. An item already >= PREPARING is NEVER modified (defense-in-depth); an absent
    // item (ticket not created yet, or line has no kitchen item) is skipped.
    if (event.cancelledLineIds() != null) {
      for (UUID orderLineId : event.cancelledLineIds()) {
        voidIfStillQueued(event.orderId(), orderLineId);
      }
    }

    // (3) Record the idempotency-ledger row LAST, in THIS transaction, so it commits atomically
    // with any item voids applied above. A concurrent-duplicate unique violation propagates and
    // rolls back the whole transaction instead of pre-committing a "processed" marker ahead of an
    // unguaranteed void (mirrors KitchenTicketCreationService's CR-01/I-WR-01 fix).
    recordProcessed(event.eventId());
  }

  private void voidIfStillQueued(UUID orderId, UUID orderLineId) {
    Optional<KitchenTicketItemEntity> locked =
        itemRepository.lockByOrderIdAndOrderLineId(orderId, orderLineId);
    if (locked.isEmpty()) {
      log.debug(
          "No kitchen item for orderId={} orderLineId={} — skipping void (ticket may not exist"
              + " yet)",
          orderId,
          orderLineId);
      return;
    }

    KitchenTicketItemEntity item = locked.get();
    if (item.getStatus() != KitchenItemStatus.QUEUED) {
      // Already advanced beyond QUEUED (or already CANCELLED) — never touch it (defense-in-depth).
      log.debug(
          "Kitchen item {} for orderLineId={} is {} (not QUEUED) — leaving untouched",
          item.getId(),
          orderLineId,
          item.getStatus());
      return;
    }

    item.setStatus(KitchenItemStatus.CANCELLED);
    itemRepository.save(item);
  }

  private void recordProcessed(UUID eventId) {
    KitchenProcessedEventEntity ledger = new KitchenProcessedEventEntity();
    ledger.setEventId(eventId);
    ledger.setConsumerName(CONSUMER_NAME);
    ledger.setProcessedAt(Instant.now());
    processedEventRepository.save(ledger);
  }
}
