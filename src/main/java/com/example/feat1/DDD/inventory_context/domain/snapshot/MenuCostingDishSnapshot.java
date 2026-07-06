package com.example.feat1.DDD.inventory_context.domain.snapshot;

import java.math.BigDecimal;
import java.util.UUID;

public record MenuCostingDishSnapshot(
    UUID dishId, String dishName, BigDecimal sellPrice, UUID categoryId) {}
