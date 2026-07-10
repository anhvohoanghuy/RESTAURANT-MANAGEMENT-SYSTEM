package com.example.feat1.DDD.order_context.application.dto;

import com.example.feat1.DDD.order_context.domain.model.OrderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class OrderCancellationDtos {
  private OrderCancellationDtos() {}

  /** Request body for a partial (specific-lines) cancel. */
  public record CancelOrderLinesRequest(List<UUID> lineIds) {}

  /**
   * Response echoing the order's post-cancel status, recomputed total, and the subset of line ids
   * that were actually cancelled by this call (excludes lines already at/after PREPARING or already
   * cancelled).
   */
  public record OrderCancellationResponse(
      UUID orderId, OrderStatus status, BigDecimal total, List<UUID> cancelledLineIds) {}
}
