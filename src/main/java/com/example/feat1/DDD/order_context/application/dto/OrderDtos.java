package com.example.feat1.DDD.order_context.application.dto;

import com.example.feat1.DDD.order_context.domain.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {
  private OrderDtos() {}

  public record SubmittedOrderResponse(
      UUID orderId,
      UUID userId,
      OrderStatus status,
      Instant submittedAt,
      SubmittedOrderTableSnapshot table,
      List<SubmittedOrderLineResponse> lines,
      BigDecimal total,
      SubmittedOrderPaymentSummary payment) {}

  public record SubmittedOrderPaymentSummary(
      String paymentStatus,
      BigDecimal paidAmount,
      BigDecimal refundedAmount,
      String refundStatus,
      BigDecimal remainingAmount) {}

  public record SubmittedOrderTableSnapshot(
      UUID tableId, String code, String name, UUID areaId, String areaName) {}

  public record SubmittedOrderLineResponse(
      UUID lineId,
      UUID dishId,
      String dishName,
      BigDecimal basePrice,
      List<SubmittedOrderToppingSnapshotResponse> selectedToppings,
      BigDecimal toppingsTotal,
      BigDecimal unitPrice,
      int quantity,
      BigDecimal lineTotal) {}

  public record SubmittedOrderToppingSnapshotResponse(
      UUID toppingGroupId,
      String toppingGroupName,
      UUID toppingOptionId,
      String toppingOptionName,
      BigDecimal additionalPrice) {}
}
