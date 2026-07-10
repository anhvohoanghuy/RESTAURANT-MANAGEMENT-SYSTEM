package com.example.feat1.DDD.order_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.order_context.application.dto.OrderCancellationDtos.CancelOrderLinesRequest;
import com.example.feat1.DDD.order_context.application.dto.OrderCancellationDtos.OrderCancellationResponse;
import com.example.feat1.DDD.order_context.application.event.OrderCancelledEvent;
import com.example.feat1.DDD.order_context.domain.model.KitchenItemStatusView;
import com.example.feat1.DDD.order_context.domain.model.OrderDomainException;
import com.example.feat1.DDD.order_context.domain.model.OrderStatus;
import com.example.feat1.DDD.order_context.domain.port.KitchenItemStatusPort;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderLineEntity;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderRepository;
import com.example.feat1.DDD.shared.outbox.application.OutboxWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class OrderCancellationServiceTest {

  private OrderRepository orderRepository;
  private KitchenItemStatusPort kitchenItemStatusPort;
  private OutboxWriter outboxWriter;
  private OrderCancellationService service;

  private final UUID userId = UUID.randomUUID();
  private final UUID orderId = UUID.randomUUID();
  private final UUID lineId1 = UUID.randomUUID();
  private final UUID lineId2 = UUID.randomUUID();
  private final UUID dishId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    orderRepository = mock(OrderRepository.class);
    kitchenItemStatusPort = mock(KitchenItemStatusPort.class);
    outboxWriter = mock(OutboxWriter.class);
    service = new OrderCancellationService(orderRepository, kitchenItemStatusPort, outboxWriter);
    ReflectionTestUtils.setField(service, "orderCancelledTopic", "orders.cancelled");
  }

  @Test
  void cancelIsRejectedWhenOrderStatusIsAtOrAfterPreparing() {
    OrderEntity order = orderWithLines(OrderStatus.PREPARING, line(lineId1, "10.00"));
    when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));
    when(orderRepository.lockById(orderId)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> service.cancelOrder(userId, orderId))
        .isInstanceOf(OrderDomainException.class)
        .hasFieldOrPropertyWithValue("code", OrderDomainException.CANCEL_WINDOW_CLOSED);

    verify(outboxWriter, never()).save(any(), any(), any(), any(), any(), any());
  }

  @Test
  void customerCancelOfAnotherUsersOrderIsRejectedAsNotFoundWithoutLocking() {
    when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.cancelOrder(userId, orderId))
        .isInstanceOf(OrderDomainException.class)
        .hasFieldOrPropertyWithValue("code", OrderDomainException.ORDER_NOT_FOUND);

    verify(orderRepository, never()).lockById(any());
    verify(outboxWriter, never()).save(any(), any(), any(), any(), any(), any());
  }

  @Test
  void wholeOrderCancelSetsCancelledAndPublishesSingleOutboxEvent() {
    OrderEntity order =
        orderWithLines(OrderStatus.CONFIRMED, line(lineId1, "10.00"), line(lineId2, "20.00"));
    when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));
    when(orderRepository.lockById(orderId)).thenReturn(Optional.of(order));
    when(kitchenItemStatusPort.findStatuses(orderId)).thenReturn(Map.of());

    OrderCancellationResponse response = service.cancelOrder(userId, orderId);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(order.getLines()).allMatch(l -> l.getCancelledAt() != null);
    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(response.cancelledLineIds()).containsExactlyInAnyOrder(lineId1, lineId2);

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(outboxWriter, times(1))
        .save(
            org.mockito.ArgumentMatchers.eq("ORDER"),
            org.mockito.ArgumentMatchers.eq(orderId),
            org.mockito.ArgumentMatchers.eq(OrderCancelledEvent.TYPE),
            org.mockito.ArgumentMatchers.eq("orders.cancelled"),
            org.mockito.ArgumentMatchers.eq(orderId.toString()),
            eventCaptor.capture());
    OrderCancelledEvent published = (OrderCancelledEvent) eventCaptor.getValue();
    assertThat(published.wholeOrder()).isTrue();
    assertThat(published.cancelledLineIds()).containsExactlyInAnyOrder(lineId1, lineId2);
    assertThat(published.totalLines()).isEqualTo(2);
  }

  @Test
  void partialCancelMarksRequestedLineAndRecomputesTotalFromRemainingLines() {
    OrderEntity order =
        orderWithLines(OrderStatus.CONFIRMED, line(lineId1, "10.00"), line(lineId2, "20.00"));
    when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));
    when(orderRepository.lockById(orderId)).thenReturn(Optional.of(order));
    when(kitchenItemStatusPort.findStatuses(orderId)).thenReturn(Map.of());

    OrderCancellationResponse response =
        service.cancelOrderLines(userId, orderId, new CancelOrderLinesRequest(List.of(lineId1)));

    OrderLineEntity cancelledLine =
        order.getLines().stream().filter(l -> l.getId().equals(lineId1)).findFirst().orElseThrow();
    OrderLineEntity remainingLine =
        order.getLines().stream().filter(l -> l.getId().equals(lineId2)).findFirst().orElseThrow();
    assertThat(cancelledLine.getCancelledAt()).isNotNull();
    assertThat(remainingLine.getCancelledAt()).isNull();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.getTotal()).isEqualByComparingTo("20.00");
    assertThat(response.cancelledLineIds()).containsExactly(lineId1);

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(outboxWriter, times(1)).save(any(), any(), any(), any(), any(), eventCaptor.capture());
    OrderCancelledEvent published = (OrderCancelledEvent) eventCaptor.getValue();
    assertThat(published.wholeOrder()).isFalse();
    assertThat(published.cancelledLineIds()).containsExactly(lineId1);
    assertThat(published.totalLines()).isEqualTo(2);
  }

  @Test
  void partialCancelExcludesLineAlreadyAtOrAfterPreparingViaKitchenPort() {
    OrderEntity order =
        orderWithLines(OrderStatus.CONFIRMED, line(lineId1, "10.00"), line(lineId2, "20.00"));
    when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));
    when(orderRepository.lockById(orderId)).thenReturn(Optional.of(order));
    when(kitchenItemStatusPort.findStatuses(orderId))
        .thenReturn(Map.of(lineId2, KitchenItemStatusView.AT_OR_AFTER_PREPARING));

    OrderCancellationResponse response =
        service.cancelOrderLines(
            userId, orderId, new CancelOrderLinesRequest(List.of(lineId1, lineId2)));

    assertThat(response.cancelledLineIds()).containsExactly(lineId1);
    OrderLineEntity preparingLine =
        order.getLines().stream().filter(l -> l.getId().equals(lineId2)).findFirst().orElseThrow();
    assertThat(preparingLine.getCancelledAt()).isNull();
    assertThat(order.getTotal()).isEqualByComparingTo("20.00");
  }

  @Test
  void partialCancelWhereAllRequestedLinesAreAlreadyPreparingRejectsWithNoPublish() {
    OrderEntity order = orderWithLines(OrderStatus.CONFIRMED, line(lineId1, "10.00"));
    when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));
    when(orderRepository.lockById(orderId)).thenReturn(Optional.of(order));
    when(kitchenItemStatusPort.findStatuses(orderId))
        .thenReturn(Map.of(lineId1, KitchenItemStatusView.AT_OR_AFTER_PREPARING));

    assertThatThrownBy(
            () ->
                service.cancelOrderLines(
                    userId, orderId, new CancelOrderLinesRequest(List.of(lineId1))))
        .isInstanceOf(OrderDomainException.class)
        .hasFieldOrPropertyWithValue("code", OrderDomainException.NO_CANCELLABLE_LINES);

    verify(outboxWriter, never()).save(any(), any(), any(), any(), any(), any());
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
  }

  @Test
  void wholeOrderCancelWithOneLineAlreadyPreparingRejectsWithNoPublishOrTerminalStatus() {
    // CR-02 regression: the race-guard exclusion (kitchen port) is per-line, but the whole-order
    // outcome must honor it too -- a whole-order cancel can never leave a still-preparing line
    // behind while forcing the order terminal and publishing wholeOrder=true (which would trigger
    // a full refund for food still being prepared).
    OrderEntity order =
        orderWithLines(OrderStatus.CONFIRMED, line(lineId1, "10.00"), line(lineId2, "20.00"));
    when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));
    when(orderRepository.lockById(orderId)).thenReturn(Optional.of(order));
    when(kitchenItemStatusPort.findStatuses(orderId))
        .thenReturn(Map.of(lineId2, KitchenItemStatusView.AT_OR_AFTER_PREPARING));

    assertThatThrownBy(() -> service.cancelOrder(userId, orderId))
        .isInstanceOf(OrderDomainException.class)
        .hasFieldOrPropertyWithValue("code", OrderDomainException.CANCEL_WINDOW_CLOSED);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.getLines()).allMatch(l -> l.getCancelledAt() == null);
    verify(outboxWriter, never()).save(any(), any(), any(), any(), any(), any());
  }

  @Test
  void staffCancelSkipsOwnershipCheckAndLocksDirectly() {
    OrderEntity order = orderWithLines(OrderStatus.CONFIRMED, line(lineId1, "10.00"));
    when(orderRepository.lockById(orderId)).thenReturn(Optional.of(order));
    when(kitchenItemStatusPort.findStatuses(orderId)).thenReturn(Map.of());

    service.cancelOrder(null, orderId);

    verify(orderRepository, never()).findByIdAndUserId(any(), any());
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
  }

  private OrderEntity orderWithLines(OrderStatus status, OrderLineEntity... lines) {
    OrderEntity order = new OrderEntity();
    order.setId(orderId);
    order.setUserId(userId);
    order.setStatus(status);
    List<OrderLineEntity> lineList = new ArrayList<>(List.of(lines));
    for (OrderLineEntity line : lineList) {
      line.setOrder(order);
    }
    order.setLines(lineList);
    order.setTotal(
        lineList.stream()
            .map(OrderLineEntity::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    return order;
  }

  private OrderLineEntity line(UUID id, String lineTotal) {
    OrderLineEntity line = new OrderLineEntity();
    line.setId(id);
    line.setDishId(dishId);
    line.setDishName("Pho Bo");
    line.setBasePrice(new BigDecimal(lineTotal));
    line.setToppingsTotal(BigDecimal.ZERO);
    line.setUnitPrice(new BigDecimal(lineTotal));
    line.setQuantity(1);
    line.setLineTotal(new BigDecimal(lineTotal));
    return line;
  }
}
