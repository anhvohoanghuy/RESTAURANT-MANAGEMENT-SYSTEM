package com.example.feat1.DDD.payment_context.infrastructure.entity;

import com.example.feat1.DDD.payment_context.domain.model.PaymentMethod;
import com.example.feat1.DDD.payment_context.domain.model.PaymentRecordStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "payments",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_payments_order_idempotency",
            columnNames = {"order_id", "idempotency_key"}))
public class PaymentEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "order_user_id", nullable = false)
  private UUID orderUserId;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentMethod method;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentRecordStatus status = PaymentRecordStatus.CONFIRMED;

  @Column(name = "idempotency_key", nullable = false)
  private String idempotencyKey;

  private String reference;

  private String note;

  @Column(name = "actor_user_id", nullable = false)
  private UUID actorUserId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @OneToMany(
      mappedBy = "payment",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<PaymentRefundEntity> refunds = new ArrayList<>();
}
