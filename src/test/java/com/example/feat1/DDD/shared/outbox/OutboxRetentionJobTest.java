package com.example.feat1.DDD.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.feat1.DDD.shared.outbox.entity.OutboxEventEntity;
import com.example.feat1.DDD.shared.outbox.infrastructure.OutboxRetentionJob;
import com.example.feat1.DDD.shared.outbox.repository.OutboxEventRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * Covers IN-02: {@link OutboxRetentionJob}'s SENT-row retention delete and FAILED-row surfacing.
 *
 * <p>{@code deletesOnlyOldSentRows...} exercises the ACTUAL repository JPQL against the real H2
 * test database ({@code @SpringBootTest @Transactional}, mirroring {@code
 * OutboxWriterPersistenceTest} — this project has no {@code @DataJpaTest} slice module on the
 * classpath) to prove the delete is filtered strictly on {@code status='SENT' AND createdAt<cutoff}
 * (T-17.2-07): an old SENT row is removed while a within-window SENT row and PENDING/FAILED rows of
 * ANY age are retained. The test-managed transaction rolls back after each method, so no seeded row
 * leaks between tests.
 *
 * <p>The remaining tests are a broker/DB-free unit test of {@link OutboxRetentionJob} itself
 * (mocked repository, mirroring {@code OutboxRelayTest}) proving its scheduled methods log the
 * expected info/warn messages. The {@code @Scheduled} trigger is never invoked — the methods are
 * called directly, keeping these DB-timing free even though the class runs under a Spring context
 * for the repository test above.
 */
@SpringBootTest
@Transactional
class OutboxRetentionJobTest {

  @Autowired private OutboxEventRepository outboxEventRepository;

  @Test
  void deletesOnlyOldSentRowsAndRetainsWithinWindowPendingAndFailedRows() {
    Instant now = Instant.now();
    Instant cutoff = now.minus(7, ChronoUnit.DAYS);

    UUID oldSentId = saveRow("SENT", now.minus(10, ChronoUnit.DAYS));
    UUID newSentId = saveRow("SENT", now.minus(1, ChronoUnit.DAYS));
    UUID oldPendingId = saveRow("PENDING", now.minus(10, ChronoUnit.DAYS));
    UUID oldFailedId = saveRow("FAILED", now.minus(10, ChronoUnit.DAYS));

    int deleted = outboxEventRepository.deleteByStatusAndCreatedAtBefore("SENT", cutoff);

    assertThat(deleted).isEqualTo(1);
    assertThat(outboxEventRepository.findById(oldSentId)).isEmpty();
    assertThat(outboxEventRepository.findById(newSentId)).isPresent();
    assertThat(outboxEventRepository.findById(oldPendingId)).isPresent();
    assertThat(outboxEventRepository.findById(oldFailedId)).isPresent();
  }

  private UUID saveRow(String status, Instant createdAt) {
    OutboxEventEntity row = new OutboxEventEntity();
    row.setAggregateType("ORDER");
    row.setAggregateId(UUID.randomUUID());
    row.setEventType("TestEvent");
    row.setTopic("test.topic");
    row.setMsgKey(UUID.randomUUID().toString());
    row.setPayload("{}");
    row.setStatus(status);
    row.setCreatedAt(createdAt);
    return outboxEventRepository.save(row).getId();
  }

  private OutboxEventRepository mockRepository;
  private OutboxRetentionJob job;
  private ListAppender<ILoggingEvent> appender;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUpMockedJob() {
    mockRepository = mock(OutboxEventRepository.class);
    job = new OutboxRetentionJob(mockRepository);
    ReflectionTestUtils.setField(job, "ttlDays", 7L);
    appender = attach();
  }

  @AfterEach
  void tearDownMockedJob() {
    detach(appender);
  }

  private static ListAppender<ILoggingEvent> attach() {
    Logger logger = (Logger) LoggerFactory.getLogger(OutboxRetentionJob.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private static void detach(ListAppender<ILoggingEvent> appender) {
    Logger logger = (Logger) LoggerFactory.getLogger(OutboxRetentionJob.class);
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void sweepSentRetentionLogsInfoWithDeletedCountWhenRowsDeleted() {
    when(mockRepository.deleteByStatusAndCreatedAtBefore(eq("SENT"), any(Instant.class)))
        .thenReturn(3);

    job.sweepSentRetention();

    assertThat(appender.list)
        .anySatisfy(
            e -> {
              assertThat(e.getLevel()).isEqualTo(Level.INFO);
              assertThat(e.getFormattedMessage()).contains("3");
            });
  }

  @Test
  void sweepSentRetentionLogsNothingWhenNoRowsDeleted() {
    when(mockRepository.deleteByStatusAndCreatedAtBefore(eq("SENT"), any(Instant.class)))
        .thenReturn(0);

    job.sweepSentRetention();

    assertThat(appender.list).isEmpty();
  }

  @Test
  void surfaceFailedRowsWarnsWithCountWhenFailedRowsExist() {
    when(mockRepository.countByStatus("FAILED")).thenReturn(5L);

    job.surfaceFailedRows();

    assertThat(appender.list)
        .anySatisfy(
            e -> {
              assertThat(e.getLevel()).isEqualTo(Level.WARN);
              assertThat(e.getFormattedMessage()).contains("5");
            });
  }

  @Test
  void surfaceFailedRowsStaysQuietWhenNoFailedRows() {
    when(mockRepository.countByStatus("FAILED")).thenReturn(0L);

    job.surfaceFailedRows();

    assertThat(appender.list).noneMatch(e -> e.getLevel() == Level.WARN);
  }
}
