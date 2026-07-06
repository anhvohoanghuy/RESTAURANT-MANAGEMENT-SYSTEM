package com.example.feat1.DDD.payment_context.domain.snapshot;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentOrderSnapshot(UUID orderId, UUID userId, BigDecimal total) {}
