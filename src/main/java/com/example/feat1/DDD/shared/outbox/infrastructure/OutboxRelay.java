package com.example.feat1.DDD.shared.outbox.infrastructure;

import com.example.feat1.DDD.shared.outbox.entity.OutboxEventEntity;
import com.example.feat1.DDD.shared.outbox.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled poller that claims {@code PENDING} outbox rows and republishes their stored JSON
 * payload verbatim via a {@code String} {@link KafkaTemplate} (I-WR-02). Gated off by default in
 * the test profile ({@code outbox.relay.enabled=false} in {@code
 * src/test/resources/application.properties}) because the {@link
 * OutboxEventRepository#claimPending} native query uses {@code FOR UPDATE SKIP LOCKED}, which H2
 * does not support.
 *
 * <p>The send is awaited INSIDE this method's transaction before the row is flipped to {@code SENT}
 * (RESEARCH Pitfall 2) — marking a row sent before the broker actually acknowledges it would reopen
 * the exact dual-write gap the outbox exists to close. A failed send increments {@code attempts}
 * and leaves the row {@code PENDING} so the next poll re-drives it (crash recovery); once {@code
 * attempts} reaches {@link #MAX_ATTEMPTS} the row is marked {@code FAILED} instead of being retried
 * forever.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
  private static final int BATCH_SIZE = 100;
  private static final int MAX_ATTEMPTS = 10;
  private static final long SEND_TIMEOUT_SECONDS = 10;

  private final OutboxEventRepository outboxEventRepository;
  private final KafkaTemplate<String, String> outboxKafkaTemplate;

  @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:1000}")
  @Transactional
  public void poll() {
    List<OutboxEventEntity> claimed = outboxEventRepository.claimPending(BATCH_SIZE);
    for (OutboxEventEntity row : claimed) {
      publish(row);
    }
  }

  private void publish(OutboxEventEntity row) {
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
  }
}
