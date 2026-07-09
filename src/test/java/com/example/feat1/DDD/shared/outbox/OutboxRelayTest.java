package com.example.feat1.DDD.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.shared.outbox.entity.OutboxEventEntity;
import com.example.feat1.DDD.shared.outbox.infrastructure.OutboxRelay;
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
 * OutboxEventRepository} and a mocked {@code KafkaTemplate<String,String>} — no EmbeddedKafka, no
 * real DB. Proves (a) a successful send flips the row to SENT with sentAt set, (b) a failed send
 * increments attempts and leaves the row re-drivable in PENDING, and (c) a still-PENDING row is
 * re-published on the next poll (crash-recovery republish).
 */
class OutboxRelayTest {

  private OutboxEventRepository outboxEventRepository;
  private KafkaTemplate<String, String> outboxKafkaTemplate;
  private OutboxRelay relay;
  private OutboxEventEntity row;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    outboxEventRepository = mock(OutboxEventRepository.class);
    outboxKafkaTemplate = mock(KafkaTemplate.class);
    relay = new OutboxRelay(outboxEventRepository, outboxKafkaTemplate);

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
}
