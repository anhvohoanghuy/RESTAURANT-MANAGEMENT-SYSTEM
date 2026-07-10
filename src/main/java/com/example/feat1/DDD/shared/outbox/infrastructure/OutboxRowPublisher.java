package com.example.feat1.DDD.shared.outbox.infrastructure;

import com.example.feat1.DDD.shared.outbox.entity.OutboxEventEntity;
import com.example.feat1.DDD.shared.outbox.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes ONE claimed outbox row and commits its resulting status change in its OWN transaction
 * (WR-02). {@link OutboxRelay#poll()} calls {@link #publish(OutboxEventEntity)} on this INJECTED
 * bean — never via self-invocation — so Spring proxies the call and opens a fresh transaction per
 * row. A slow or failed send on one claimed row can therefore no longer roll back sibling rows in
 * the same poll batch that were already sent and marked {@code SENT}, bounding duplicate-publish
 * amplification on the next poll to at most the single in-flight row instead of the whole batch.
 *
 * <p>The send is still awaited INSIDE this transaction before the row is flipped to {@code SENT}
 * (send-then-mark ordering, CR-01-adjacent durability guarantee from Phase 17.1) — a row is never
 * marked SENT before the broker actually acknowledges it. A failed send increments {@code attempts}
 * and leaves the row {@code PENDING} so the next poll re-drives it (crash recovery); once {@code
 * attempts} reaches {@link #MAX_ATTEMPTS} the row is marked {@code FAILED} instead of being retried
 * forever.
 *
 * <p>This class is purely a per-row COMMIT-boundary refactor. It deliberately does NOT reintroduce
 * a {@code REQUIRES_NEW} pre-commit idempotency-ledger pattern — that would revert the Phase 17.1
 * CR-01 fix, which is out of scope here.
 */
@Component
@RequiredArgsConstructor
public class OutboxRowPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxRowPublisher.class);
  private static final int MAX_ATTEMPTS = 10;
  private static final long SEND_TIMEOUT_SECONDS = 10;

  private final OutboxEventRepository outboxEventRepository;
  private final KafkaTemplate<String, String> outboxKafkaTemplate;

  @Transactional
  public void publish(OutboxEventEntity row) {
    try {
      outboxKafkaTemplate
          .send(row.getTopic(), row.getMsgKey(), row.getPayload())
          .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      row.setStatus("SENT");
      row.setSentAt(Instant.now());
    } catch (Exception ex) {
      row.setAttempts(row.getAttempts() + 1);
      if (row.getAttempts() >= MAX_ATTEMPTS) {
        row.setStatus("FAILED");
        log.error(
            "Outbox row {} FAILED permanently after {} attempts aggregateType={} eventType={} topic={}",
            row.getId(),
            row.getAttempts(),
            row.getAggregateType(),
            row.getEventType(),
            row.getTopic(),
            ex);
      } else {
        log.warn(
            "Outbox row {} publish attempt {} failed aggregateType={} eventType={} topic={} — will retry",
            row.getId(),
            row.getAttempts(),
            row.getAggregateType(),
            row.getEventType(),
            row.getTopic(),
            ex);
      }
    }
    outboxEventRepository.save(row);
  }
}
