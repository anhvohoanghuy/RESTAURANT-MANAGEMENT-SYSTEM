package com.example.feat1.DDD.inventory_context.infrastructure.entity;

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
 * Inventory-context idempotency ledger. A unique {@code (event_id, consumer_name)} pair guards
 * against duplicate delivery: the reservation service inserts + flushes a row and treats a unique
 * violation as a replay. This is the Inventory ledger table and is physically distinct from the
 * Order context's {@code order_processed_events} table (one datasource / one schema means two
 * entities cannot map the same physical table name).
 */
@Getter
@Setter
@Entity
@Table(
    name = "inventory_processed_events",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_inventory_processed_event",
            columnNames = {"event_id", "consumer_name"}))
public class InventoryProcessedEventEntity {

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
