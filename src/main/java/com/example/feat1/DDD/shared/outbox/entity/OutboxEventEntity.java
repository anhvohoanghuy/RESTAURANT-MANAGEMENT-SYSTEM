package com.example.feat1.DDD.shared.outbox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Shared transactional-outbox row (I-WR-02). A business {@code @Transactional} method persists a
 * {@code PENDING} row in the SAME transaction as its state change, closing the dual-write gap
 * between DB commit and a Kafka send. A separate {@code @Scheduled} relay ({@code OutboxRelay})
 * later claims and publishes {@code PENDING} rows, flipping them to {@code SENT} only after the
 * broker send succeeds.
 *
 * <p>This table is shared across contexts (Order + Inventory) rather than split per-context,
 * because it is pure infrastructure plumbing with no context-specific columns — see
 * 17.1-RESEARCH.md Open Question #1.
 */
@Getter
@Setter
@Entity
@Table(
    name = "outbox_events",
    indexes = @Index(name = "ix_outbox_status_created", columnList = "status, created_at"))
public class OutboxEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "aggregate_type", nullable = false)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "topic", nullable = false)
  private String topic;

  @Column(name = "msg_key", nullable = false)
  private String msgKey;

  // @Lob (not columnDefinition="LONGTEXT") for a portable long-text column: H2 CLOB in tests,
  // MySQL LONGTEXT in production.
  @Lob
  @Column(name = "payload", nullable = false)
  private String payload;

  @Column(name = "status", nullable = false)
  private String status = "PENDING";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "attempts", nullable = false)
  private int attempts = 0;
}
