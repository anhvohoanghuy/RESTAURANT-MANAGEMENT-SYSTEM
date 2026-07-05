package com.example.feat1.DDD.order_context.domain.snapshot;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderMenuQuote(
    UUID dishId,
    String dishName,
    BigDecimal basePrice,
    List<OrderToppingSnapshot> selectedToppings,
    BigDecimal toppingsTotal,
    BigDecimal unitPrice) {}
