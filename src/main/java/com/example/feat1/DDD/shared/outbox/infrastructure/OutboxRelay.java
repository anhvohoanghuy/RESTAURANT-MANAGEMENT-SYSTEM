package com.example.feat1.DDD.shared.outbox.infrastructure;

import com.example.feat1.DDD.shared.outbox.entity.OutboxEventEntity;
import com.example.feat1.DDD.shared.outbox.repository.OutboxEventRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled poller that claims {@code PENDING} outbox rows and republishes their stored JSON
 * payload verbatim via a {@code String} {@link KafkaTemplate} (I-WR-02). Gated off by default in
 * the test profile ({@code outbox.relay.enabled=false} in {@code
 * src/test/resources/application.properties}) because the {@link
 * OutboxEventRepository#claimPending} native query uses {@code FOR UPDATE SKIP LOCKED}, which H2
 * does not support.
 *
 * <p>{@code poll()} itself is deliberately NOT {@code @Transactional}: it only claims the batch,
 * then delegates each row to {@link OutboxRowPublisher#publish(OutboxEventEntity)} through the
 * INJECTED bean (never self-invocation), so each row's send + status flip commits in its OWN
 * transaction (WR-02). A slow or failed send on one claimed row can no longer roll back sibling
 * rows in the same batch that were already sent and marked {@code SENT}, bounding duplicate-publish
 * amplification on the next poll to at most the single in-flight row instead of the whole batch.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

  private static final int BATCH_SIZE = 100;

  private final OutboxEventRepository outboxEventRepository;
  private final OutboxRowPublisher outboxRowPublisher;

  @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:1000}")
  public void poll() {
    List<OutboxEventEntity> claimed = outboxEventRepository.claimPending(BATCH_SIZE);
    for (OutboxEventEntity row : claimed) {
      outboxRowPublisher.publish(row);
    }
  }
}
