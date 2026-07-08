package com.example.feat1.DDD.kitchen_context.application.dto;

import com.example.feat1.DDD.kitchen_context.domain.model.KitchenItemStatus;
import java.util.UUID;

/** View of a kitchen ticket item returned after a successful advance. */
public record KitchenItemResponse(
    UUID itemId,
    UUID orderId,
    UUID orderLineId,
    String dishName,
    int quantity,
    KitchenItemStatus status) {}
