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
 * A per-order-line release record — the structural inverse of {@link
 * InventoryLineSettlementEntity}. The named unique {@code (order_id, order_line_id)} constraint is
 * the durable double-release guard (T-18-03-01); counting rows per {@code order_id} lets the
 * release service (and the widened settlement completion guard) detect the last resolved line
 * without inspecting reservation lines.
 */
@Getter
@Setter
@Entity
@Table(
    name = "inventory_line_releases",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_inventory_line_release",
            columnNames = {"order_id", "order_line_id"}))
public class InventoryLineReleaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "order_line_id", nullable = false)
  private UUID orderLineId;

  @Column(name = "released_at", nullable = false)
  private Instant releasedAt;
}
