package com.example.feat1.DDD.order_context.application;

import com.example.feat1.DDD.order_context.application.dto.OrderDtos.SubmittedOrderLineResponse;
import com.example.feat1.DDD.order_context.application.dto.OrderDtos.SubmittedOrderResponse;
import com.example.feat1.DDD.order_context.application.dto.OrderDtos.SubmittedOrderTableSnapshot;
import com.example.feat1.DDD.order_context.application.dto.OrderDtos.SubmittedOrderToppingSnapshotResponse;
import com.example.feat1.DDD.order_context.application.event.OrderCreatedEvent;
import com.example.feat1.DDD.order_context.domain.model.OrderDomainException;
import com.example.feat1.DDD.order_context.domain.model.OrderStatus;
import com.example.feat1.DDD.order_context.domain.port.OrderEventPublisher;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderCartEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderCartLineEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderCartLineToppingSnapshot;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderLineEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderLineToppingSnapshot;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderCartLineRepository;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderCartRepository;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class OrderSubmissionService {
  private final OrderCartRepository cartRepository;
  private final OrderCartLineRepository cartLineRepository;
  private final OrderRepository orderRepository;
  private final OrderEventPublisher orderEventPublisher;

  @Transactional
  public SubmittedOrderResponse submit(UUID userId) {
    OrderCartEntity cart =
        cartRepository.findByUserId(userId).orElseThrow(OrderDomainException::cartEmpty);
    List<OrderCartLineEntity> cartLines =
        cartLineRepository.findByCart_IdOrderByIdAsc(cart.getId());
    if (cartLines.isEmpty()) {
      throw OrderDomainException.cartEmpty();
    }
    if (cart.getTableId() == null) {
      throw OrderDomainException.cartTableRequired();
    }

    OrderEntity order = new OrderEntity();
    order.setUserId(userId);
    order.setStatus(OrderStatus.SUBMITTED);
    order.setSubmittedAt(Instant.now());
    order.setTableId(cart.getTableId());
    order.setTableCode(cart.getTableCode());
    order.setTableName(cart.getTableName());
    order.setAreaId(cart.getAreaId());
    order.setAreaName(cart.getAreaName());

    List<OrderLineEntity> orderLines =
        cartLines.stream().map(line -> toOrderLine(order, line)).toList();
    order.setLines(new ArrayList<>(orderLines));
    order.setTotal(
        orderLines.stream()
            .map(OrderLineEntity::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add));

    OrderEntity saved = orderRepository.save(order);
    cartLineRepository.deleteByCart_Id(cart.getId());
    clearCartTable(cart);
    cartRepository.save(cart);

    SubmittedOrderResponse response = toResponse(saved);
    publishAfterCommit(toEvent(response));
    return response;
  }

  @Transactional(readOnly = true)
  public List<SubmittedOrderResponse> listOrders(UUID userId) {
    return orderRepository.findByUserIdOrderBySubmittedAtDesc(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public SubmittedOrderResponse getOrder(UUID userId, UUID orderId) {
    return orderRepository
        .findByIdAndUserId(orderId, userId)
        .map(this::toResponse)
        .orElseThrow(OrderDomainException::orderNotFound);
  }

  private OrderLineEntity toOrderLine(OrderEntity order, OrderCartLineEntity cartLine) {
    OrderLineEntity line = new OrderLineEntity();
    line.setOrder(order);
    line.setDishId(cartLine.getDishId());
    line.setToppingKey(cartLine.getToppingKey());
    line.setDishName(cartLine.getDishName());
    line.setBasePrice(cartLine.getBasePrice());
    line.setSelectedToppings(
        cartLine.getSelectedToppings().stream().map(this::toOrderTopping).toList());
    line.setToppingsTotal(cartLine.getToppingsTotal());
    line.setUnitPrice(cartLine.getUnitPrice());
    line.setQuantity(cartLine.getQuantity());
    line.setLineTotal(cartLine.getUnitPrice().multiply(BigDecimal.valueOf(cartLine.getQuantity())));
    return line;
  }

  private OrderLineToppingSnapshot toOrderTopping(OrderCartLineToppingSnapshot cartTopping) {
    OrderLineToppingSnapshot topping = new OrderLineToppingSnapshot();
    topping.setToppingGroupId(cartTopping.getToppingGroupId());
    topping.setToppingGroupName(cartTopping.getToppingGroupName());
    topping.setToppingOptionId(cartTopping.getToppingOptionId());
    topping.setToppingOptionName(cartTopping.getToppingOptionName());
    topping.setAdditionalPrice(cartTopping.getAdditionalPrice());
    return topping;
  }

  private void clearCartTable(OrderCartEntity cart) {
    cart.setTableId(null);
    cart.setTableCode(null);
    cart.setTableName(null);
    cart.setAreaId(null);
    cart.setAreaName(null);
  }

  private SubmittedOrderResponse toResponse(OrderEntity order) {
    List<SubmittedOrderLineResponse> lines =
        order.getLines().stream().map(this::toLineResponse).toList();
    return new SubmittedOrderResponse(
        order.getId(),
        order.getUserId(),
        order.getStatus(),
        order.getSubmittedAt(),
        new SubmittedOrderTableSnapshot(
            order.getTableId(),
            order.getTableCode(),
            order.getTableName(),
            order.getAreaId(),
            order.getAreaName()),
        lines,
        order.getTotal());
  }

  private SubmittedOrderLineResponse toLineResponse(OrderLineEntity line) {
    return new SubmittedOrderLineResponse(
        line.getId(),
        line.getDishId(),
        line.getDishName(),
        line.getBasePrice(),
        line.getSelectedToppings().stream()
            .map(
                topping ->
                    new SubmittedOrderToppingSnapshotResponse(
                        topping.getToppingGroupId(),
                        topping.getToppingGroupName(),
                        topping.getToppingOptionId(),
                        topping.getToppingOptionName(),
                        topping.getAdditionalPrice()))
            .toList(),
        line.getToppingsTotal(),
        line.getUnitPrice(),
        line.getQuantity(),
        line.getLineTotal());
  }

  private OrderCreatedEvent toEvent(SubmittedOrderResponse order) {
    return new OrderCreatedEvent(
        UUID.randomUUID(),
        OrderCreatedEvent.TYPE,
        Instant.now(),
        order.orderId(),
        order.userId(),
        new OrderCreatedEvent.OrderTable(
            order.table().tableId(),
            order.table().code(),
            order.table().name(),
            order.table().areaId(),
            order.table().areaName()),
        order.lines().stream()
            .map(
                line ->
                    new OrderCreatedEvent.OrderLine(
                        line.lineId(),
                        line.dishId(),
                        line.dishName(),
                        line.basePrice(),
                        line.selectedToppings().stream()
                            .map(
                                topping ->
                                    new OrderCreatedEvent.OrderTopping(
                                        topping.toppingGroupId(),
                                        topping.toppingGroupName(),
                                        topping.toppingOptionId(),
                                        topping.toppingOptionName(),
                                        topping.additionalPrice()))
                            .toList(),
                        line.toppingsTotal(),
                        line.unitPrice(),
                        line.quantity(),
                        line.lineTotal()))
            .toList(),
        order.total(),
        order.submittedAt());
  }

  private void publishAfterCommit(OrderCreatedEvent event) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      orderEventPublisher.publishOrderCreated(event);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            orderEventPublisher.publishOrderCreated(event);
          }
        });
  }
}
