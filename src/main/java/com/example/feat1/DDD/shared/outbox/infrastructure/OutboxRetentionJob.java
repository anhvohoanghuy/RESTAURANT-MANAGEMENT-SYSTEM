package com.example.feat1.DDD.shared.outbox.infrastructure;

import com.example.feat1.DDD.shared.outbox.repository.OutboxEventRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled sweep that (a) deletes {@code SENT} outbox rows past a configurable retention window
 * (IN-02, mitigates T-17.2-05 unbounded {@code outbox_events} growth slowing {@code claimPending})
 * and (b) surfaces {@code FAILED} rows via a periodic {@code log.warn} count (IN-02, mitigates
 * T-17.2-06 silent poison-row death). Gated off by default in the test profile ({@code
 * outbox.retention.enabled=false} in {@code src/test/resources/application.properties}), mirroring
 * {@link OutboxRelay}'s gate, so tests stay DB-timing free.
 *
 * <p>The retention delete filters strictly on {@code status='SENT' AND createdAt<cutoff} (T-17.2-07
 * mitigation) — {@code PENDING}/{@code FAILED} rows are never touched by this sweep regardless of
 * age, so an in-flight or poison row can never be silently discarded.
 *
 * <p>No {@code MeterRegistry}/Micrometer dependency is present on this project's classpath (grep of
 * {@code pom.xml} confirms no {@code spring-boot-starter-actuator}/{@code micrometer} artifact), so
 * FAILED-row surfacing is log-only per the plan's discretion clause — no new dependency is added.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "outbox.retention.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxRetentionJob {

  private static final Logger log = LoggerFactory.getLogger(OutboxRetentionJob.class);

  private final OutboxEventRepository outboxEventRepository;

  @Value("${outbox.retention.ttl-days:7}")
  private long ttlDays;

  /** Deletes SENT rows older than {@code ttlDays}. Runs hourly by default. */
  @Scheduled(fixedDelayString = "${outbox.retention.delay-ms:3600000}")
  @Transactional
  public void sweepSentRetention() {
    if (ttlDays < 1) {
      log.warn("Outbox retention ttl-days is misconfigured ({}); skipping sweep", ttlDays);
      return;
    }
    Instant cutoff = Instant.now().minus(ttlDays, ChronoUnit.DAYS);
    int deleted = outboxEventRepository.deleteByStatusAndCreatedAtBefore("SENT", cutoff);
    if (deleted > 0) {
      log.info("Outbox retention deleted {} SENT rows older than {}", deleted, cutoff);
    }
  }

  /** Counts FAILED rows and warns if any exist so a poison message is visible to operators. */
  @Scheduled(fixedDelayString = "${outbox.retention.failed-check-delay-ms:3600000}")
  public void surfaceFailedRows() {
    long failedCount = outboxEventRepository.countByStatus("FAILED");
    if (failedCount > 0) {
      log.warn("Outbox has {} FAILED row(s) awaiting operator attention", failedCount);
    }
  }
}
