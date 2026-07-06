package com.example.feat1.DDD.order_context.domain.port;

import com.example.feat1.DDD.order_context.domain.snapshot.OrderPaymentSummary;
import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentSummaryPort {
  OrderPaymentSummary summarize(UUID orderId, BigDecimal orderTotal);
}
