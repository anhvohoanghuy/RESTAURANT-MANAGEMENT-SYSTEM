package com.example.feat1.DDD.order_context.application;

import com.example.feat1.DDD.order_context.application.dto.CartDtos.AddCartItemRequest;
import com.example.feat1.DDD.order_context.application.dto.CartDtos.CartLineResponse;
import com.example.feat1.DDD.order_context.application.dto.CartDtos.CartResponse;
import com.example.feat1.DDD.order_context.application.dto.CartDtos.CartTableSnapshotResponse;
import com.example.feat1.DDD.order_context.application.dto.CartDtos.CartToppingSnapshotResponse;
import com.example.feat1.DDD.order_context.application.dto.CartDtos.UpdateCartLineQuantityRequest;
import com.example.feat1.DDD.order_context.domain.model.CartStatus;
import com.example.feat1.DDD.order_context.domain.model.OrderDomainException;
import com.example.feat1.DDD.order_context.domain.port.MenuQuotePort;
import com.example.feat1.DDD.order_context.domain.port.TableSessionValidationPort;
import com.example.feat1.DDD.order_context.domain.port.TableValidationPort;
import com.example.feat1.DDD.order_context.domain.snapshot.OrderMenuQuote;
import com.example.feat1.DDD.order_context.domain.snapshot.OrderTableSessionSnapshot;
import com.example.feat1.DDD.order_context.domain.snapshot.OrderTableSnapshot;
import com.example.feat1.DDD.order_context.domain.snapshot.OrderToppingSnapshot;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderCartEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderCartLineEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderCartLineToppingSnapshot;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderCartLineRepository;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderCartRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {
  private final OrderCartRepository cartRepository;
  private final OrderCartLineRepository lineRepository;
  private final MenuQuotePort menuQuotePort;
  private final TableValidationPort tableValidationPort;
  private final TableSessionValidationPort tableSessionValidationPort;

  @Transactional
  public CartResponse getCart(UUID userId) {
    return toResponse(ensureCart(userId));
  }

  @Transactional
  public CartResponse addItem(UUID userId, AddCartItemRequest request) {
    int quantity = requirePositiveQuantity(request == null ? null : request.quantity());
    UUID tableId = request == null ? null : request.tableId();
    if (tableId == null) {
      throw OrderDomainException.tableRequired();
    }

    OrderCartEntity cart = ensureCart(userId);
    OrderTableSnapshot table = tableValidationPort.validate(tableId);
    UUID requestedSessionId = request.tableSessionId();
    assertSameOrEmptySession(cart, requestedSessionId);
    UUID sessionIdToValidate =
        requestedSessionId == null ? cart.getTableSessionId() : requestedSessionId;
    OrderTableSessionSnapshot session =
        tableSessionValidationPort.validateOpenSession(sessionIdToValidate, tableId);
    assertSameOrEmptyTable(cart, table.tableId());
    applyTableSnapshot(cart, table, session);

    List<UUID> normalizedToppings = normalizeToppingOptionIds(request.toppingOptionIds());
    String toppingKey = toppingKey(normalizedToppings);
    OrderMenuQuote quote = menuQuotePort.quote(request.dishId(), normalizedToppings);

    OrderCartLineEntity line =
        lineRepository
            .findByCart_IdAndDishIdAndToppingKey(cart.getId(), quote.dishId(), toppingKey)
            .orElseGet(
                () -> {
                  OrderCartLineEntity created = new OrderCartLineEntity();
                  created.setCart(cart);
                  created.setDishId(quote.dishId());
                  created.setToppingKey(toppingKey);
                  created.setQuantity(0);
                  return created;
                });
    applyMenuSnapshot(line, quote);
    line.setQuantity(line.getQuantity() + quantity);
    lineRepository.save(line);
    cartRepository.save(cart);

    return toResponse(cart);
  }

  @Transactional
  public CartResponse updateLineQuantity(
      UUID userId, UUID lineId, UpdateCartLineQuantityRequest request) {
    int quantity = requirePositiveQuantity(request == null ? null : request.quantity());
    OrderCartEntity cart = ensureCart(userId);
    OrderCartLineEntity line =
        lineRepository
            .findByCart_IdAndId(cart.getId(), lineId)
            .orElseThrow(OrderDomainException::cartLineNotFound);
    line.setQuantity(quantity);
    lineRepository.save(line);
    return toResponse(cart);
  }

  @Transactional
  public CartResponse removeLine(UUID userId, UUID lineId) {
    OrderCartEntity cart = ensureCart(userId);
    OrderCartLineEntity line =
        lineRepository
            .findByCart_IdAndId(cart.getId(), lineId)
            .orElseThrow(OrderDomainException::cartLineNotFound);
    lineRepository.delete(line);
    if (lineRepository.countByCart_Id(cart.getId()) == 0) {
      clearTableSnapshot(cart);
      cartRepository.save(cart);
    }
    return toResponse(cart);
  }

  @Transactional
  public CartResponse clearCart(UUID userId) {
    OrderCartEntity cart = ensureCart(userId);
    lineRepository.deleteByCart_Id(cart.getId());
    clearTableSnapshot(cart);
    return toResponse(cartRepository.save(cart));
  }

  private OrderCartEntity ensureCart(UUID userId) {
    if (userId == null) {
      throw new IllegalArgumentException("User id is required");
    }
    return cartRepository
        .findByUserId(userId)
        .orElseGet(
            () -> {
              OrderCartEntity cart = new OrderCartEntity();
              cart.setUserId(userId);
              cart.setStatus(CartStatus.ACTIVE);
              return cartRepository.save(cart);
            });
  }

  private int requirePositiveQuantity(Integer quantity) {
    if (quantity == null || quantity <= 0) {
      throw OrderDomainException.quantityInvalid();
    }
    return quantity;
  }

  private void assertSameOrEmptyTable(OrderCartEntity cart, UUID tableId) {
    if (cart.getTableId() != null && !cart.getTableId().equals(tableId)) {
      throw OrderDomainException.cartTableMismatch();
    }
  }

  private void assertSameOrEmptySession(OrderCartEntity cart, UUID tableSessionId) {
    if (cart.getTableSessionId() != null
        && tableSessionId != null
        && !cart.getTableSessionId().equals(tableSessionId)) {
      throw OrderDomainException.cartTableMismatch();
    }
  }

  private void applyTableSnapshot(
      OrderCartEntity cart, OrderTableSnapshot table, OrderTableSessionSnapshot session) {
    cart.setTableId(table.tableId());
    cart.setTableSessionId(session == null ? null : session.sessionId());
    cart.setTableCode(table.code());
    cart.setTableName(table.name());
    cart.setAreaId(table.areaId());
    cart.setAreaName(table.areaName());
  }

  private void clearTableSnapshot(OrderCartEntity cart) {
    cart.setTableId(null);
    cart.setTableSessionId(null);
    cart.setTableCode(null);
    cart.setTableName(null);
    cart.setAreaId(null);
    cart.setAreaName(null);
  }

  private void applyMenuSnapshot(OrderCartLineEntity line, OrderMenuQuote quote) {
    line.setDishName(quote.dishName());
    line.setBasePrice(quote.basePrice());
    line.setToppingsTotal(quote.toppingsTotal());
    line.setUnitPrice(quote.unitPrice());
    line.setSelectedToppings(
        new ArrayList<>(quote.selectedToppings().stream().map(this::toEntity).toList()));
  }

  private OrderCartLineToppingSnapshot toEntity(OrderToppingSnapshot topping) {
    OrderCartLineToppingSnapshot entity = new OrderCartLineToppingSnapshot();
    entity.setToppingGroupId(topping.toppingGroupId());
    entity.setToppingGroupName(topping.toppingGroupName());
    entity.setToppingOptionId(topping.toppingOptionId());
    entity.setToppingOptionName(topping.toppingOptionName());
    entity.setAdditionalPrice(topping.additionalPrice());
    return entity;
  }

  private List<UUID> normalizeToppingOptionIds(List<UUID> toppingOptionIds) {
    if (toppingOptionIds == null) {
      return List.of();
    }
    return toppingOptionIds.stream()
        .filter(id -> id != null)
        .collect(
            java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                set -> set.stream().sorted(Comparator.comparing(UUID::toString)).toList()));
  }

  private String toppingKey(List<UUID> toppingOptionIds) {
    return toppingOptionIds.stream()
        .map(UUID::toString)
        .collect(java.util.stream.Collectors.joining(","));
  }

  private CartResponse toResponse(OrderCartEntity cart) {
    List<CartLineResponse> lines =
        lineRepository.findByCart_IdOrderByIdAsc(cart.getId()).stream()
            .map(this::toLineResponse)
            .toList();
    BigDecimal total =
        lines.stream().map(CartLineResponse::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new CartResponse(cart.getId(), cart.getUserId(), toTableResponse(cart), lines, total);
  }

  private CartTableSnapshotResponse toTableResponse(OrderCartEntity cart) {
    if (cart.getTableId() == null) {
      return null;
    }
    return new CartTableSnapshotResponse(
        cart.getTableId(),
        cart.getTableSessionId(),
        cart.getTableCode(),
        cart.getTableName(),
        cart.getAreaId(),
        cart.getAreaName());
  }

  private CartLineResponse toLineResponse(OrderCartLineEntity line) {
    BigDecimal lineTotal = line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity()));
    return new CartLineResponse(
        line.getId(),
        line.getDishId(),
        line.getDishName(),
        line.getBasePrice(),
        line.getSelectedToppings().stream()
            .map(
                topping ->
                    new CartToppingSnapshotResponse(
                        topping.getToppingGroupId(),
                        topping.getToppingGroupName(),
                        topping.getToppingOptionId(),
                        topping.getToppingOptionName(),
                        topping.getAdditionalPrice()))
            .toList(),
        line.getToppingsTotal(),
        line.getUnitPrice(),
        line.getQuantity(),
        lineTotal);
  }
}
