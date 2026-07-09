package com.example.feat1.DDD.shared.outbox.application;

import com.example.feat1.DDD.shared.outbox.entity.OutboxEventEntity;
import com.example.feat1.DDD.shared.outbox.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Persists a {@code PENDING} {@link OutboxEventEntity} row atomically with the caller's business
 * state change (I-WR-02). {@code propagation = MANDATORY} means this method only ever runs inside
 * an already-open transaction — the caller's {@code @Transactional} business method — so the outbox
 * row and the state mutation share ONE commit. Calling this outside a transaction is a programming
 * error and fails fast rather than silently opening a new one.
 *
 * <p>This is the contract plans 06/07 (order + inventory saga cutover) call instead of publishing
 * directly via {@code afterCommit}. The event is serialized to JSON at write time and republished
 * verbatim (byte-identical wire payload) by {@code OutboxRelay} via a {@code String} {@code
 * KafkaTemplate}.
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper objectMapper;

  @Transactional(propagation = Propagation.MANDATORY)
  public void save(
      String aggregateType,
      UUID aggregateId,
      String eventType,
      String topic,
      String msgKey,
      Object event) {
    String payload = objectMapper.writeValueAsString(event);

    OutboxEventEntity row = new OutboxEventEntity();
    row.setAggregateType(aggregateType);
    row.setAggregateId(aggregateId);
    row.setEventType(eventType);
    row.setTopic(topic);
    row.setMsgKey(msgKey);
    row.setPayload(payload);
    row.setStatus("PENDING");
    row.setCreatedAt(Instant.now());
    row.setAttempts(0);

    outboxEventRepository.save(row);
  }
}
