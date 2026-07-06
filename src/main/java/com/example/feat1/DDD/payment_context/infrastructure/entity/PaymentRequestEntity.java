package com.example.feat1.DDD.payment_context.infrastructure.entity;

import com.example.feat1.DDD.payment_context.domain.model.PaymentMethod;
import com.example.feat1.DDD.payment_context.domain.model.PaymentRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "payment_requests")
public class PaymentRequestEntity {
  @Id private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "order_user_id", nullable = false)
  private UUID orderUserId;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentMethod method = PaymentMethod.QR_CODE;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentRequestStatus status = PaymentRequestStatus.PENDING;

  @Column(name = "payment_url", nullable = false)
  private String paymentUrl;

  @Column(name = "redirect_url", nullable = false)
  private String redirectUrl;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;
}
