package com.example.feat1.DDD.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.feat1.DDD.shared.outbox.application.OutboxWriter;
import com.example.feat1.DDD.shared.outbox.entity.OutboxEventEntity;
import com.example.feat1.DDD.shared.outbox.repository.OutboxEventRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves {@link OutboxWriter#save} persists exactly one PENDING outbox row atomically inside the
 * CALLER's transaction (I-WR-02). The test class runs each test method in Spring's test-managed
 * transaction, so {@code @Transactional(propagation = MANDATORY)} on {@code OutboxWriter.save}
 * participates in it rather than throwing.
 *
 * <p>Uses {@code findByStatusOrderByCreatedAtAsc} — the portable derived finder — NOT {@code
 * claimPending}, because the native {@code FOR UPDATE SKIP LOCKED} query is MySQL-8-only and must
 * never execute against the H2 test database.
 */
@SpringBootTest
@Transactional
class OutboxWriterPersistenceTest {

  @Autowired private OutboxWriter outboxWriter;
  @Autowired private OutboxEventRepository outboxEventRepository;

  @Test
  void saveInsidesTransactionPersistsSinglePendingRowThatRoundTripsIntact() {
    UUID aggregateId = UUID.randomUUID();
    String longPayload = "x".repeat(300);
    TestEvent event = new TestEvent(aggregateId.toString(), longPayload);

    outboxWriter.save(
        "ORDER", aggregateId, "TestEvent", "test.topic", aggregateId.toString(), event);

    List<OutboxEventEntity> pending =
        outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING");

    assertThat(pending).hasSize(1);
    OutboxEventEntity row = pending.get(0);
    assertThat(row.getStatus()).isEqualTo("PENDING");
    assertThat(row.getAttempts()).isZero();
    assertThat(row.getAggregateType()).isEqualTo("ORDER");
    assertThat(row.getAggregateId()).isEqualTo(aggregateId);
    assertThat(row.getEventType()).isEqualTo("TestEvent");
    assertThat(row.getTopic()).isEqualTo("test.topic");
    assertThat(row.getMsgKey()).isEqualTo(aggregateId.toString());
    assertThat(row.getPayload()).contains(longPayload);
    assertThat(row.getPayload().length()).isGreaterThan(255);
  }

  private record TestEvent(String id, String note) {}
}
