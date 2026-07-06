package com.example.feat1.DDD.payment_context.domain.port;

import com.example.feat1.DDD.payment_context.domain.snapshot.PaymentOrderSnapshot;
import java.util.UUID;

public interface OrderPaymentLookupPort {
  PaymentOrderSnapshot getSubmittedOrder(UUID orderId);
}
