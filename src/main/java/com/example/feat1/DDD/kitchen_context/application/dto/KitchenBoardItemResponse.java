package com.example.feat1.DDD.kitchen_context.application.dto;

import com.example.feat1.DDD.kitchen_context.domain.model.KitchenItemStatus;
import java.util.UUID;

/** Row of the kitchen board: one active (not-yet-COMPLETED) ticket item. */
public record KitchenBoardItemResponse(
    UUID itemId,
    UUID orderId,
    UUID orderLineId,
    String dishName,
    int quantity,
    KitchenItemStatus status) {}
