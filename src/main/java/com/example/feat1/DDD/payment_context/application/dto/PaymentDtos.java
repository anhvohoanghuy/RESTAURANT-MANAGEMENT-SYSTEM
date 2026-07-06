package com.example.feat1.DDD.payment_context.application.dto;

import com.example.feat1.DDD.payment_context.domain.model.PaymentMethod;
import com.example.feat1.DDD.payment_context.domain.model.PaymentRequestStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PaymentDtos {
  private PaymentDtos() {}

  public record RecordPaymentRequest(
      BigDecimal amount,
      PaymentMethod method,
      String idempotencyKey,
      String reference,
      String note) {}

  public record RecordRefundRequest(BigDecimal amount, String idempotencyKey, String reason) {}

  public record CreateQrPaymentRequest(BigDecimal amount) {}

  public record PaymentResponse(
      UUID paymentId,
      UUID orderId,
      UUID orderUserId,
      BigDecimal amount,
      PaymentMethod method,
      String status,
      String reference,
      String note,
      Instant createdAt,
      List<RefundResponse> refunds) {}

  public record RefundResponse(
      UUID refundId, UUID paymentId, BigDecimal amount, String reason, Instant createdAt) {}

  public record PaymentSummaryResponse(
      String paymentStatus,
      BigDecimal paidAmount,
      BigDecimal refundedAmount,
      String refundStatus,
      BigDecimal remainingAmount) {}

  public record QrPaymentRequestResponse(
      UUID requestId,
      UUID orderId,
      BigDecimal amount,
      PaymentRequestStatus status,
      String paymentUrl,
      String redirectUrl,
      Instant createdAt,
      Instant expiresAt) {}

  public record PaymentHistoryResponse(
      List<PaymentResponse> items, String nextCursor, boolean hasMore) {}
}
