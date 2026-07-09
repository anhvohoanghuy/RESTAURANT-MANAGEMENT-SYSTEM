package com.example.feat1.DDD.order_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.order_context.application.event.OrderCreatedEvent;
import com.example.feat1.DDD.order_context.domain.model.CartStatus;
import com.example.feat1.DDD.order_context.domain.model.OrderDomainException;
import com.example.feat1.DDD.order_context.domain.port.PaymentSummaryPort;
import com.example.feat1.DDD.order_context.domain.snapshot.OrderPaymentSummary;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderCartEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderCartLineEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderCartLineToppingSnapshot;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderEntity;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderCartLineRepository;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderCartRepository;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderRepository;
import com.example.feat1.DDD.shared.outbox.application.OutboxWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class OrderSubmissionServiceTest {
  private OrderCartRepository cartRepository;
  private OrderCartLineRepository cartLineRepository;
  private OrderRepository orderRepository;
  private OutboxWriter outboxWriter;
  private PaymentSummaryPort paymentSummaryPort;
  private OrderSubmissionService service;

  private final UUID userId = UUID.randomUUID();
  private final UUID cartId = UUID.randomUUID();
  private final UUID tableId = UUID.randomUUID();
  private final UUID areaId = UUID.randomUUID();
  private final UUID dishId = UUID.randomUUID();
  private final UUID toppingGroupId = UUID.randomUUID();
  private final UUID toppingOptionId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    cartRepository = mock(OrderCartRepository.class);
    cartLineRepository = mock(OrderCartLineRepository.class);
    orderRepository = mock(OrderRepository.class);
    outboxWriter = mock(OutboxWriter.class);
    paymentSummaryPort = mock(PaymentSummaryPort.class);
    service =
        new OrderSubmissionService(
            cartRepository, cartLineRepository, orderRepository, outboxWriter, paymentSummaryPort);
    ReflectionTestUtils.setField(service, "orderCreatedTopic", "orders.created");

    when(cartRepository.save(any(OrderCartEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(orderRepository.save(any(OrderEntity.class)))
        .thenAnswer(
            invocation -> {
              OrderEntity order = invocation.getArgument(0);
              if (order.getId() == null) {
                order.setId(UUID.randomUUID());
              }
              order
                  .getLines()
                  .forEach(
                      line -> {
                        if (line.getId() == null) {
                          line.setId(UUID.randomUUID());
                        }
                      });
              return order;
            });
    when(paymentSummaryPort.summarize(any(), any()))
        .thenAnswer(invocation -> OrderPaymentSummary.unpaid(invocation.getArgument(1)));
  }

  @Test
  void submitCopiesCartSnapshotsClearsCartAndPublishesEvent() {
    OrderCartEntity cart = cartWithTable();
    UUID tableSessionId = cart.getTableSessionId();
    OrderCartLineEntity cartLine = cartLine(cart);
    when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
    when(cartLineRepository.findByCart_IdOrderByIdAsc(cartId)).thenReturn(List.of(cartLine));

    var response = service.submit(userId);

    assertThat(response.table().tableId()).isEqualTo(tableId);
    assertThat(response.table().tableSessionId()).isEqualTo(tableSessionId);
    assertThat(response.table().code()).isEqualTo("A01");
    assertThat(response.lines()).hasSize(1);
    assertThat(response.lines().get(0).dishId()).isEqualTo(dishId);
    assertThat(response.lines().get(0).selectedToppings()).hasSize(1);
    assertThat(response.total()).isEqualByComparingTo("160000");
    assertThat(response.payment().paymentStatus()).isEqualTo("UNPAID");
    assertThat(cart.getTableId()).isNull();
    assertThat(cart.getTableSessionId()).isNull();

    verify(cartLineRepository).deleteByCart_Id(cartId);
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
    assertThat(aggregateIdCaptor.getValue()).isEqualTo(response.orderId());
    assertThat(eventTypeCaptor.getValue()).isEqualTo(OrderCreatedEvent.TYPE);
    assertThat(topicCaptor.getValue()).isEqualTo("orders.created");
    assertThat(msgKeyCaptor.getValue()).isEqualTo(response.orderId().toString());

    OrderCreatedEvent event = (OrderCreatedEvent) eventCaptor.getValue();
    assertThat(event.eventType()).isEqualTo(OrderCreatedEvent.TYPE);
    assertThat(event.orderId()).isEqualTo(response.orderId());
    assertThat(event.userId()).isEqualTo(userId);
    assertThat(event.table().tableId()).isEqualTo(tableId);
    assertThat(event.table().tableSessionId()).isEqualTo(response.table().tableSessionId());
    assertThat(event.lines()).hasSize(1);
    assertThat(event.total()).isEqualByComparingTo("160000");
  }

  @Test
  void submitRejectsEmptyCartAndDoesNotPublish() {
    OrderCartEntity cart = cartWithTable();
    when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
    when(cartLineRepository.findByCart_IdOrderByIdAsc(cartId)).thenReturn(List.of());

    assertThatThrownBy(() -> service.submit(userId))
        .isInstanceOf(OrderDomainException.class)
        .extracting("code")
        .isEqualTo(OrderDomainException.CART_EMPTY);
    verify(outboxWriter, never()).save(any(), any(), any(), any(), any(), any());
  }

  @Test
  void submitRejectsCartWithoutTableAndDoesNotPublish() {
    OrderCartEntity cart = cartWithTable();
    cart.setTableId(null);
    when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
    when(cartLineRepository.findByCart_IdOrderByIdAsc(cartId)).thenReturn(List.of(cartLine(cart)));

    assertThatThrownBy(() -> service.submit(userId))
        .isInstanceOf(OrderDomainException.class)
        .extracting("code")
        .isEqualTo(OrderDomainException.CART_TABLE_REQUIRED);
    verify(outboxWriter, never()).save(any(), any(), any(), any(), any(), any());
  }

  @Test
  void getOrderIsOwnerScoped() {
    UUID orderId = UUID.randomUUID();
    when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getOrder(userId, orderId))
        .isInstanceOf(OrderDomainException.class)
        .extracting("code")
        .isEqualTo(OrderDomainException.ORDER_NOT_FOUND);
  }

  private OrderCartEntity cartWithTable() {
    OrderCartEntity cart = new OrderCartEntity();
    cart.setId(cartId);
    cart.setUserId(userId);
    cart.setStatus(CartStatus.ACTIVE);
    cart.setTableId(tableId);
    cart.setTableSessionId(UUID.randomUUID());
    cart.setTableCode("A01");
    cart.setTableName("Table A01");
    cart.setAreaId(areaId);
    cart.setAreaName("Main Hall");
    return cart;
  }

  private OrderCartLineEntity cartLine(OrderCartEntity cart) {
    OrderCartLineToppingSnapshot topping = new OrderCartLineToppingSnapshot();
    topping.setToppingGroupId(toppingGroupId);
    topping.setToppingGroupName("Sauce");
    topping.setToppingOptionId(toppingOptionId);
    topping.setToppingOptionName("Chili");
    topping.setAdditionalPrice(BigDecimal.valueOf(10000));

    OrderCartLineEntity line = new OrderCartLineEntity();
    line.setId(UUID.randomUUID());
    line.setCart(cart);
    line.setDishId(dishId);
    line.setToppingKey(toppingOptionId.toString());
    line.setDishName("Pho Bo");
    line.setBasePrice(BigDecimal.valueOf(70000));
    line.setSelectedToppings(List.of(topping));
    line.setToppingsTotal(BigDecimal.valueOf(10000));
    line.setUnitPrice(BigDecimal.valueOf(80000));
    line.setQuantity(2);
    return line;
  }
}
