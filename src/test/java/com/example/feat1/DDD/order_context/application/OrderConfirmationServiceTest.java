package com.example.feat1.DDD.order_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent;
import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent.Result;
import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent.Shortfall;
import com.example.feat1.DDD.order_context.domain.model.OrderStatus;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderEntity;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderProcessedEventRepository;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderConfirmationServiceTest {
  private OrderProcessedEventRepository processedEventRepository;
  private OrderRepository orderRepository;
  private OrderConfirmationService service;

  private final UUID eventId = UUID.randomUUID();
  private final UUID orderId = UUID.randomUUID();
  private final UUID ingredientId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    processedEventRepository = mock(OrderProcessedEventRepository.class);
    orderRepository = mock(OrderRepository.class);
    service = new OrderConfirmationService(processedEventRepository, orderRepository);
  }

  @Test
  void confirmedResultTransitionsPendingOrderToConfirmed() {
    OrderEntity order = orderWithStatus(OrderStatus.PENDING_CONFIRMATION);
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    service.onStockResult(confirmedEvent());

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.getRejectionReason()).isNull();
  }

  @Test
  void rejectedResultTransitionsPendingOrderToRejectedWithReason() {
    OrderEntity order = orderWithStatus(OrderStatus.PENDING_CONFIRMATION);
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    service.onStockResult(rejectedEvent());

    assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
    assertThat(order.getRejectionReason()).isNotBlank().contains("Pho Broth");
  }

  @Test
  void duplicateEventIsNoOpAndNeverLoadsOrder() {
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(true);

    service.onStockResult(confirmedEvent());

    verify(orderRepository, never()).findById(any());
    verify(processedEventRepository, never()).saveAndFlush(any());
  }

  @Test
  void nonPendingOrderIsNotTransitioned() {
    OrderEntity order = orderWithStatus(OrderStatus.CONFIRMED);
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    service.onStockResult(rejectedEvent());

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.getRejectionReason()).isNull();
  }

  private OrderEntity orderWithStatus(OrderStatus status) {
    OrderEntity order = new OrderEntity();
    order.setId(orderId);
    order.setStatus(status);
    return order;
  }

  private OrderStockResultEvent confirmedEvent() {
    return new OrderStockResultEvent(
        eventId,
        OrderStockResultEvent.CONFIRMED_TYPE,
        Instant.now(),
        orderId,
        Result.CONFIRMED,
        List.of());
  }

  private OrderStockResultEvent rejectedEvent() {
    Shortfall shortfall =
        new Shortfall(ingredientId, "Pho Broth", new BigDecimal("10.0"), new BigDecimal("2.0"));
    return new OrderStockResultEvent(
        eventId,
        OrderStockResultEvent.REJECTED_TYPE,
        Instant.now(),
        orderId,
        Result.REJECTED,
        List.of(shortfall));
  }
}
