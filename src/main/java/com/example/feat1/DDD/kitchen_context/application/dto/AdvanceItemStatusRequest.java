package com.example.feat1.DDD.kitchen_context.application.dto;

import com.example.feat1.DDD.kitchen_context.domain.model.KitchenItemStatus;

/**
 * PATCH body for advancing a single kitchen ticket item to the next status. The service enforces
 * that {@code targetStatus} is exactly one forward step from the item's current status (D-02).
 */
public record AdvanceItemStatusRequest(KitchenItemStatus targetStatus) {}
