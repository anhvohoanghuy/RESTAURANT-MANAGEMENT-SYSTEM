package com.example.feat1.DDD.payment_context.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "payment_refunds",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_payment_refunds_idempotency",
            columnNames = {"payment_id", "idempotency_key"}))
public class PaymentRefundEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payment_id", nullable = false)
  private PaymentEntity payment;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(name = "idempotency_key", nullable = false)
  private String idempotencyKey;

  private String reason;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
