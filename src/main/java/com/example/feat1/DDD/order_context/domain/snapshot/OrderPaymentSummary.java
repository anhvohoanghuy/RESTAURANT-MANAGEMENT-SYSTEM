package com.example.feat1.DDD.order_context.domain.snapshot;

import java.math.BigDecimal;

public record OrderPaymentSummary(
    String paymentStatus,
    BigDecimal paidAmount,
    BigDecimal refundedAmount,
    String refundStatus,
    BigDecimal remainingAmount) {
  public static OrderPaymentSummary unpaid(BigDecimal orderTotal) {
    return new OrderPaymentSummary("UNPAID", BigDecimal.ZERO, BigDecimal.ZERO, "NONE", orderTotal);
  }
}
