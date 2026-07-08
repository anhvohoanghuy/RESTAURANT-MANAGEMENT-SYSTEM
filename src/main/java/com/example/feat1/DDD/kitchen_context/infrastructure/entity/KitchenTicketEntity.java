package com.example.feat1.DDD.kitchen_context.infrastructure.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Kitchen fulfillment aggregate root. One row per confirmed order — a unique constraint on {@code
 * order_id} enforces the one-ticket-per-order invariant (D-02).
 */
@Getter
@Setter
@Entity
@Table(
    name = "kitchen_tickets",
    uniqueConstraints =
        @UniqueConstraint(name = "uq_kitchen_ticket_order", columnNames = "order_id"))
public class KitchenTicketEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @OneToMany(
      mappedBy = "ticket",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<KitchenTicketItemEntity> items = new ArrayList<>();
}
