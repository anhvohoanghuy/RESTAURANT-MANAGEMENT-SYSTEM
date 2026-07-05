package com.example.feat1.DDD.order_context.domain.snapshot;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderToppingSnapshot(
    UUID toppingGroupId,
    String toppingGroupName,
    UUID toppingOptionId,
    String toppingOptionName,
    BigDecimal additionalPrice) {}
