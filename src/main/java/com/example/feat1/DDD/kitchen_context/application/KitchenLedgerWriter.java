package com.example.feat1.DDD.kitchen_context.application;

import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenProcessedEventEntity;
import com.example.feat1.DDD.kitchen_context.infrastructure.repository.KitchenProcessedEventRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the kitchen idempotency-ledger row in its OWN {@code REQUIRES_NEW} transaction (I-WR-01,
 * mirroring {@code InventoryLedgerWriter}). Isolating the {@code (event_id, consumer_name)} insert
 * in a separate bean method means a concurrent-duplicate {@link DataIntegrityViolationException}
 * rolls back only this inner transaction — it never marks the caller's ticket-creation transaction
 * rollback-only.
 *
 * <p>This exists as a distinct {@link Component} (not a private method on {@code
 * KitchenTicketCreationService}) so {@code REQUIRES_NEW} actually takes effect via the Spring
 * transaction proxy; a self-invoked method would bypass the proxy and share the caller's
 * transaction.
 */
@Component
@RequiredArgsConstructor
public class KitchenLedgerWriter {
  private static final Logger log = LoggerFactory.getLogger(KitchenLedgerWriter.class);

  private final KitchenProcessedEventRepository processedEventRepository;

  /**
   * Attempts to record the idempotency-ledger row for {@code (eventId, consumerName)} in a fresh
   * suspended transaction. Returns {@code true} when the row is inserted (first delivery); returns
   * {@code false} — without propagating — when the unique constraint rejects a concurrent
   * duplicate, so the caller can treat the event as already handled without poisoning its own
   * transaction.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean tryInsert(UUID eventId, String consumerName) {
    try {
      KitchenProcessedEventEntity ledger = new KitchenProcessedEventEntity();
      ledger.setEventId(eventId);
      ledger.setConsumerName(consumerName);
      ledger.setProcessedAt(Instant.now());
      // saveAndFlush forces the INSERT now so a concurrent-duplicate violation surfaces here inside
      // this isolated transaction rather than at outer-transaction commit time.
      processedEventRepository.saveAndFlush(ledger);
      return true;
    } catch (DataIntegrityViolationException duplicate) {
      log.debug(
          "Concurrent duplicate ledger insert eventId={} consumer={} — skipping",
          eventId,
          consumerName);
      return false;
    }
  }
}
