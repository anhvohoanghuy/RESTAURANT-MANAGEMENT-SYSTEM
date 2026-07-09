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
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderLineEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderLineToppingSnapshot;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderProcessedEventRepository;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderRepository;
import com.example.feat1.DDD.shared.outbox.application.OutboxWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class OrderConfirmationServiceTest {
  private OrderProcessedEventRepository processedEventRepository;
  private OrderRepository orderRepository;
  private OrderLedgerWriter ledgerWriter;
  private OutboxWriter outboxWriter;
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
    ledgerWriter = mock(OrderLedgerWriter.class);
    outboxWriter = mock(OutboxWriter.class);
    service =
        new OrderConfirmationService(
            processedEventRepository, orderRepository, ledgerWriter, outboxWriter);
    ReflectionTestUtils.setField(service, "orderConfirmedTopic", "orders.confirmed");
  }

  @Test
  void confirmedResultTransitionsPendingOrderToConfirmedAndWritesOutboxRowInTx() {
    OrderEntity order = orderWithStatus(OrderStatus.PENDING_CONFIRMATION);
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
    when(ledgerWriter.tryInsert(eventId, OrderConfirmationService.CONSUMER_NAME)).thenReturn(true);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    service.onStockResult(confirmedEvent());

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.getRejectionReason()).isNull();

    ArgumentCaptor<String> aggregateTypeCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<UUID> aggregateIdCaptor = ArgumentCaptor.forClass(UUID.class);
    ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> msgKeyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(outboxWriter, times(1))
        .save(
            aggregateTypeCaptor.capture(),
            aggregateIdCaptor.capture(),
            eventTypeCaptor.capture(),
            topicCaptor.capture(),
            msgKeyCaptor.capture(),
            eventCaptor.capture());

    assertThat(aggregateTypeCaptor.getValue()).isEqualTo("ORDER");
    assertThat(aggregateIdCaptor.getValue()).isEqualTo(orderId);
    assertThat(eventTypeCaptor.getValue()).isEqualTo(OrderConfirmedEvent.TYPE);
    assertThat(topicCaptor.getValue()).isEqualTo("orders.confirmed");
    assertThat(msgKeyCaptor.getValue()).isEqualTo(orderId.toString());

    OrderConfirmedEvent published = (OrderConfirmedEvent) eventCaptor.getValue();
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
    when(ledgerWriter.tryInsert(eventId, OrderConfirmationService.CONSUMER_NAME)).thenReturn(true);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    service.onStockResult(rejectedEvent());

    assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
    assertThat(order.getRejectionReason()).isNotBlank().contains("Pho Broth");
    verify(outboxWriter, never()).save(any(), any(), any(), any(), any(), any());
  }

  @Test
  void rejectedResultWithManyShortfallsCapsRejectionReasonAtMaxLength() {
    OrderEntity order = orderWithStatus(OrderStatus.PENDING_CONFIRMATION);
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
    when(ledgerWriter.tryInsert(eventId, OrderConfirmationService.CONSUMER_NAME)).thenReturn(true);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    List<Shortfall> manyShortfalls = new ArrayList<>();
    for (int i = 0; i < 2000; i++) {
      manyShortfalls.add(
          new Shortfall(
              UUID.randomUUID(), "Ingredient-" + i, new BigDecimal("10.0"), new BigDecimal("2.0")));
    }
    OrderStockResultEvent event =
        new OrderStockResultEvent(
            eventId,
            OrderStockResultEvent.REJECTED_TYPE,
            java.time.Instant.now(),
            orderId,
            Result.REJECTED,
            manyShortfalls);

    service.onStockResult(event);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
    assertThat(order.getRejectionReason()).isNotNull();
    assertThat(order.getRejectionReason().length())
        .isEqualTo(OrderConfirmationService.MAX_REASON_LEN);
    verify(outboxWriter, never()).save(any(), any(), any(), any(), any(), any());
  }

  @Test
  void duplicateEventFastPreCheckIsNoOpAndNeverLoadsOrder() {
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(true);

    service.onStockResult(confirmedEvent());

    verify(orderRepository, never()).findById(any());
    verify(ledgerWriter, never()).tryInsert(any(), any());
    verify(outboxWriter, never()).save(any(), any(), any(), any(), any(), any());
  }

  @Test
  void concurrentDuplicateLedgerInsertIsNoOpAndDoesNotThrow() {
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
    when(ledgerWriter.tryInsert(eventId, OrderConfirmationService.CONSUMER_NAME)).thenReturn(false);

    service.onStockResult(confirmedEvent());

    verify(orderRepository, never()).findById(any());
    verify(outboxWriter, never()).save(any(), any(), any(), any(), any(), any());
  }

  @Test
  void nonPendingOrderIsNotTransitioned() {
    OrderEntity order = orderWithStatus(OrderStatus.CONFIRMED);
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
    when(ledgerWriter.tryInsert(eventId, OrderConfirmationService.CONSUMER_NAME)).thenReturn(true);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    service.onStockResult(rejectedEvent());

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.getRejectionReason()).isNull();
    verify(outboxWriter, never()).save(any(), any(), any(), any(), any(), any());
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
        java.time.Instant.now(),
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
        java.time.Instant.now(),
        orderId,
        Result.REJECTED,
        List.of(shortfall));
  }
}
