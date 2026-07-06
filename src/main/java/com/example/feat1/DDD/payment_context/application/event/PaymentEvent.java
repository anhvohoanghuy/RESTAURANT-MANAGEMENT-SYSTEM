package com.example.feat1.DDD.payment_context.application.event;

import com.example.feat1.DDD.payment_context.domain.model.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    UUID orderId,
    UUID paymentId,
    UUID refundId,
    BigDecimal amount,
    PaymentMethod method,
    UUID actorUserId) {
  public static final String PAYMENT_RECORDED = "PaymentRecorded";
  public static final String PAYMENT_REFUNDED = "PaymentRefunded";
  public static final String ORDER_PAYMENT_COMPLETED = "OrderPaymentCompleted";
}
