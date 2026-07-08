package com.example.feat1.DDD.order_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.order_context.application.event.OrderConfirmedEvent;
import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent;
import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent.Result;
import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent.Shortfall;
import com.example.feat1.DDD.order_context.domain.model.OrderStatus;
import com.example.feat1.DDD.order_context.domain.port.OrderEventPublisher;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderLineEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderLineToppingSnapshot;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderProcessedEventRepository;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class OrderConfirmationServiceTest {
  private OrderProcessedEventRepository processedEventRepository;
  private OrderRepository orderRepository;
  private OrderEventPublisher orderEventPublisher;
  private OrderConfirmationService service;

  private final UUID eventId = UUID.randomUUID();
  private final UUID orderId = UUID.randomUUID();
  private final UUID ingredientId = UUID.randomUUID();
  private final UUID lineId = UUID.randomUUID();
  private final UUID dishId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    processedEventRepository = mock(OrderProcessedEventRepository.class);
    orderRepository = mock(OrderRepository.class);
    orderEventPublisher = mock(OrderEventPublisher.class);
    service =
        new OrderConfirmationService(
            processedEventRepository, orderRepository, orderEventPublisher);
  }

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void confirmedResultTransitionsPendingOrderToConfirmed() {
    OrderEntity order = orderWithStatus(OrderStatus.PENDING_CONFIRMATION);
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    service.onStockResult(confirmedEvent());

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.getRejectionReason()).isNull();

    ArgumentCaptor<OrderConfirmedEvent> captor = ArgumentCaptor.forClass(OrderConfirmedEvent.class);
    verify(orderEventPublisher, times(1)).publishOrderConfirmed(captor.capture());
    OrderConfirmedEvent published = captor.getValue();
    assertThat(published.orderId()).isEqualTo(orderId);
    assertThat(published.lines()).hasSize(1);
    OrderConfirmedEvent.OrderConfirmedLine publishedLine = published.lines().get(0);
    assertThat(publishedLine.lineId()).isEqualTo(lineId);
    assertThat(publishedLine.dishId()).isEqualTo(dishId);
    assertThat(publishedLine.dishName()).isEqualTo("Pho Bo");
    assertThat(publishedLine.quantity()).isEqualTo(2);
    assertThat(publishedLine.selectedToppings()).hasSize(1);
  }

  @Test
  void rejectedResultTransitionsPendingOrderToRejectedWithReason() {
    OrderEntity order = orderWithStatus(OrderStatus.PENDING_CONFIRMATION);
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    service.onStockResult(rejectedEvent());

    assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
    assertThat(order.getRejectionReason()).isNotBlank().contains("Pho Broth");
    verify(orderEventPublisher, never()).publishOrderConfirmed(any());
  }

  @Test
  void duplicateEventIsNoOpAndNeverLoadsOrder() {
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(true);

    service.onStockResult(confirmedEvent());

    verify(orderRepository, never()).findById(any());
    verify(processedEventRepository, never()).saveAndFlush(any());
    verify(orderEventPublisher, never()).publishOrderConfirmed(any());
  }

  @Test
  void nonPendingOrderIsNotTransitioned() {
    OrderEntity order = orderWithStatus(OrderStatus.CONFIRMED);
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    service.onStockResult(rejectedEvent());

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.getRejectionReason()).isNull();
    verify(orderEventPublisher, never()).publishOrderConfirmed(any());
  }

  @Test
  void confirmedResultRegistersPublishAsAfterCommitSynchronizationNotMidTransaction() {
    OrderEntity order = orderWithStatus(OrderStatus.PENDING_CONFIRMATION);
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    TransactionSynchronizationManager.initSynchronization();
    try {
      service.onStockResult(confirmedEvent());

      // Not invoked mid-transaction: the synchronization is only registered, not yet run.
      verify(orderEventPublisher, never()).publishOrderConfirmed(any());

      List<TransactionSynchronization> synchronizations =
          TransactionSynchronizationManager.getSynchronizations();
      assertThat(synchronizations).isNotEmpty();
      synchronizations.forEach(TransactionSynchronization::afterCommit);

      verify(orderEventPublisher, times(1)).publishOrderConfirmed(any());
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private OrderEntity orderWithStatus(OrderStatus status) {
    OrderEntity order = new OrderEntity();
    order.setId(orderId);
    order.setStatus(status);

    OrderLineToppingSnapshot topping = new OrderLineToppingSnapshot();
    topping.setToppingGroupId(UUID.randomUUID());
    topping.setToppingGroupName("Size");
    topping.setToppingOptionId(UUID.randomUUID());
    topping.setToppingOptionName("Large");
    topping.setAdditionalPrice(new BigDecimal("1.00"));

    OrderLineEntity line = new OrderLineEntity();
    line.setId(lineId);
    line.setOrder(order);
    line.setDishId(dishId);
    line.setDishName("Pho Bo");
    line.setBasePrice(new BigDecimal("5.00"));
    line.setSelectedToppings(List.of(topping));
    line.setToppingsTotal(new BigDecimal("1.00"));
    line.setUnitPrice(new BigDecimal("6.00"));
    line.setQuantity(2);
    line.setLineTotal(new BigDecimal("12.00"));

    order.setLines(new ArrayList<>(List.of(line)));
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
