package com.example.feat1.DDD.payment_context.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Payment-context idempotency ledger. A unique {@code (event_id, consumer_name)} pair guards
 * against duplicate delivery: the auto-refund service inserts a row (saved last in the same
 * transaction) and treats a subsequent replay of the same event as a no-op. This is the Payment
 * ledger table and is physically distinct from the Inventory/Order contexts' processed-events
 * tables (one datasource / one schema means two entities cannot map the same physical table name).
 */
@Getter
@Setter
@Entity
@Table(
    name = "payment_processed_events",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_payment_processed_event",
            columnNames = {"event_id", "consumer_name"}))
public class PaymentProcessedEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "consumer_name", nullable = false)
  private String consumerName;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;
}
