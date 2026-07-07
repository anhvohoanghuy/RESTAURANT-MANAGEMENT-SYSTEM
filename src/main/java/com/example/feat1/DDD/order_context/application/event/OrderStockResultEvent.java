package com.example.feat1.DDD.order_context.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderStockResultEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    UUID orderId,
    Result result,
    List<Shortfall> shortfalls) {
  public static final String CONFIRMED_TYPE = "OrderStockConfirmed";
  public static final String REJECTED_TYPE = "OrderStockRejected";

  public enum Result {
    CONFIRMED,
    REJECTED
  }

  public record Shortfall(
      UUID ingredientId, String ingredientName, BigDecimal required, BigDecimal available) {}
}
