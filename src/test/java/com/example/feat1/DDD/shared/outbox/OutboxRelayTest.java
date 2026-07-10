package com.example.feat1.DDD.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.shared.outbox.entity.OutboxEventEntity;
import com.example.feat1.DDD.shared.outbox.infrastructure.OutboxRelay;
import com.example.feat1.DDD.shared.outbox.infrastructure.OutboxRowPublisher;
import com.example.feat1.DDD.shared.outbox.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Broker-free unit test: {@link OutboxRelay} is constructed directly with a mocked {@link
 * OutboxEventRepository} and a real {@link OutboxRowPublisher} wrapping a mocked {@code
 * KafkaTemplate<String,String>} — no EmbeddedKafka, no real DB. Proves (a) a successful send flips
 * the row to SENT with sentAt set, (b) a failed send increments attempts and leaves the row
 * re-drivable in PENDING, (c) a still-PENDING row is re-published on the next poll (crash-recovery
 * republish), and (d) a send failure on one claimed row does NOT roll back or re-drive sibling rows
 * already marked SENT in the same poll batch (WR-02 — per-row transaction isolation).
 */
class OutboxRelayTest {

  private OutboxEventRepository outboxEventRepository;
  private KafkaTemplate<String, String> outboxKafkaTemplate;
  private OutboxRowPublisher rowPublisher;
  private OutboxRelay relay;
  private OutboxEventEntity row;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    outboxEventRepository = mock(OutboxEventRepository.class);
    outboxKafkaTemplate = mock(KafkaTemplate.class);
    rowPublisher = new OutboxRowPublisher(outboxEventRepository, outboxKafkaTemplate);
    relay = new OutboxRelay(outboxEventRepository, rowPublisher);

    row = new OutboxEventEntity();
    row.setId(UUID.randomUUID());
    row.setAggregateType("ORDER");
    row.setAggregateId(UUID.randomUUID());
    row.setEventType("OrderCreated");
    row.setTopic("orders.created");
    row.setMsgKey(UUID.randomUUID().toString());
    row.setPayload("{\"orderId\":\"abc\"}");
    row.setStatus("PENDING");
    row.setCreatedAt(Instant.now());
    row.setAttempts(0);

    when(outboxEventRepository.claimPending(anyInt())).thenReturn(List.of(row));
  }

  private static OutboxEventEntity newRow(String payload) {
    OutboxEventEntity r = new OutboxEventEntity();
    r.setId(UUID.randomUUID());
    r.setAggregateType("ORDER");
    r.setAggregateId(UUID.randomUUID());
    r.setEventType("OrderCreated");
    r.setTopic("orders.created");
    r.setMsgKey(UUID.randomUUID().toString());
    r.setPayload(payload);
    r.setStatus("PENDING");
    r.setCreatedAt(Instant.now());
    r.setAttempts(0);
    return r;
  }

  @Test
  @SuppressWarnings("unchecked")
  void successfulSendFlipsRowToSentWithTimestamp() {
    SendResult<String, String> mockResult = mock(SendResult.class);
    when(outboxKafkaTemplate.send(row.getTopic(), row.getMsgKey(), row.getPayload()))
        .thenReturn(CompletableFuture.completedFuture(mockResult));

    relay.poll();

    assertThat(row.getStatus()).isEqualTo("SENT");
    assertThat(row.getSentAt()).isNotNull();
    assertThat(row.getAttempts()).isZero();
  }

  @Test
  void failedSendIncrementsAttemptsAndLeavesRowPending() {
    when(outboxKafkaTemplate.send(row.getTopic(), row.getMsgKey(), row.getPayload()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

    relay.poll();

    assertThat(row.getStatus()).isEqualTo("PENDING");
    assertThat(row.getAttempts()).isEqualTo(1);
    assertThat(row.getSentAt()).isNull();
  }

  @Test
  void crashRecoveryRepublishesAStillPendingRowOnNextPoll() {
    when(outboxKafkaTemplate.send(row.getTopic(), row.getMsgKey(), row.getPayload()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

    relay.poll(); // simulated crash / transient failure — row remains PENDING
    relay.poll(); // next scheduled poll re-claims and re-publishes the still-PENDING row

    verify(outboxKafkaTemplate, times(2)).send(row.getTopic(), row.getMsgKey(), row.getPayload());
    assertThat(row.getStatus()).isEqualTo("PENDING");
    assertThat(row.getAttempts()).isEqualTo(2);
  }

  @Test
  @SuppressWarnings("unchecked")
  void siblingRowsAreNotRolledBackWhenMiddleRowFails() {
    OutboxEventEntity ok1 = newRow("{\"orderId\":\"ok1\"}");
    OutboxEventEntity failing = newRow("{\"orderId\":\"fail\"}");
    OutboxEventEntity ok2 = newRow("{\"orderId\":\"ok2\"}");
    when(outboxEventRepository.claimPending(anyInt())).thenReturn(List.of(ok1, failing, ok2));

    SendResult<String, String> mockResult = mock(SendResult.class);
    when(outboxKafkaTemplate.send(ok1.getTopic(), ok1.getMsgKey(), ok1.getPayload()))
        .thenReturn(CompletableFuture.completedFuture(mockResult));
    when(outboxKafkaTemplate.send(failing.getTopic(), failing.getMsgKey(), failing.getPayload()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
    when(outboxKafkaTemplate.send(ok2.getTopic(), ok2.getMsgKey(), ok2.getPayload()))
        .thenReturn(CompletableFuture.completedFuture(mockResult));

    // Spy on the injected publisher bean so we can assert poll() delegates each row through it
    // individually (a separate transactional boundary per row), rather than one call for the
    // whole batch.
    OutboxRowPublisher spiedPublisher = spy(rowPublisher);
    OutboxRelay isolatedRelay = new OutboxRelay(outboxEventRepository, spiedPublisher);

    isolatedRelay.poll();

    verify(spiedPublisher, times(1)).publish(ok1);
    verify(spiedPublisher, times(1)).publish(failing);
    verify(spiedPublisher, times(1)).publish(ok2);

    // The middle row's send failure does not roll back or re-drive the sibling rows already
    // committed as SENT in the same poll batch.
    assertThat(ok1.getStatus()).isEqualTo("SENT");
    assertThat(ok1.getSentAt()).isNotNull();
    assertThat(ok1.getAttempts()).isZero();

    assertThat(ok2.getStatus()).isEqualTo("SENT");
    assertThat(ok2.getSentAt()).isNotNull();
    assertThat(ok2.getAttempts()).isZero();

    // Send-then-mark ordering still holds: the failed row is never flipped to SENT before an ack.
    assertThat(failing.getStatus()).isEqualTo("PENDING");
    assertThat(failing.getAttempts()).isEqualTo(1);
    assertThat(failing.getSentAt()).isNull();
  }
}
