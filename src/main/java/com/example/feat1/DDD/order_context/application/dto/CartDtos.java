package com.example.feat1.DDD.order_context.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class CartDtos {
  private CartDtos() {}

  public record AddCartItemRequest(
      UUID tableId,
      UUID tableSessionId,
      UUID dishId,
      List<UUID> toppingOptionIds,
      Integer quantity) {}

  public record UpdateCartLineQuantityRequest(Integer quantity) {}

  public record CartResponse(
      UUID cartId,
      UUID userId,
      CartTableSnapshotResponse table,
      List<CartLineResponse> lines,
      BigDecimal total) {}

  public record CartTableSnapshotResponse(
      UUID tableId, UUID tableSessionId, String code, String name, UUID areaId, String areaName) {}

  public record CartLineResponse(
      UUID lineId,
      UUID dishId,
      String dishName,
      BigDecimal basePrice,
      List<CartToppingSnapshotResponse> selectedToppings,
      BigDecimal toppingsTotal,
      BigDecimal unitPrice,
      int quantity,
      BigDecimal lineTotal) {}

  public record CartToppingSnapshotResponse(
      UUID toppingGroupId,
      String toppingGroupName,
      UUID toppingOptionId,
      String toppingOptionName,
      BigDecimal additionalPrice) {}
}
