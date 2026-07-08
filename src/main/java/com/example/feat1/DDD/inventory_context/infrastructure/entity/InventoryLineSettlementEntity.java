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
 * A per-order-line settlement record. The named unique {@code (order_id, order_line_id)} constraint
 * is the durable double-settlement guard (D-05); counting rows per {@code order_id} lets the
 * settlement service detect the last settled line without inspecting reservation lines (D-04). One
 * table serves both concerns.
 */
@Getter
@Setter
@Entity
@Table(
    name = "inventory_line_settlements",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_inventory_line_settlement",
            columnNames = {"order_id", "order_line_id"}))
public class InventoryLineSettlementEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "order_line_id", nullable = false)
  private UUID orderLineId;

  @Column(name = "settled_at", nullable = false)
  private Instant settledAt;
}
