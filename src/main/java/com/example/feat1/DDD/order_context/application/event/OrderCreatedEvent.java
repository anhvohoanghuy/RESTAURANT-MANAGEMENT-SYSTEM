package com.example.feat1.DDD.order_context.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    UUID orderId,
    UUID userId,
    OrderTable table,
    List<OrderLine> lines,
    BigDecimal total,
    Instant submittedAt) {
  public static final String TYPE = "OrderCreated";

  public record OrderTable(UUID tableId, String code, String name, UUID areaId, String areaName) {}

  public record OrderLine(
      UUID lineId,
      UUID dishId,
      String dishName,
      BigDecimal basePrice,
      List<OrderTopping> selectedToppings,
      BigDecimal toppingsTotal,
      BigDecimal unitPrice,
      int quantity,
      BigDecimal lineTotal) {}

  public record OrderTopping(
      UUID toppingGroupId,
      String toppingGroupName,
      UUID toppingOptionId,
      String toppingOptionName,
      BigDecimal additionalPrice) {}
}
