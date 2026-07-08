package com.example.feat1.DDD.order_context.application.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderConfirmedEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    UUID orderId,
    List<OrderConfirmedLine> lines) {
  public static final String TYPE = "OrderConfirmed";

  public record OrderConfirmedLine(
      UUID lineId,
      UUID dishId,
      String dishName,
      int quantity,
      List<OrderConfirmedTopping> selectedToppings) {}

  public record OrderConfirmedTopping(
      UUID toppingGroupId,
      String toppingGroupName,
      UUID toppingOptionId,
      String toppingOptionName) {}
}
