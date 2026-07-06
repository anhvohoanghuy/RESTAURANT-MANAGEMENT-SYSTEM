package com.example.feat1.DDD.order_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.order_context.application.dto.CartDtos.AddCartItemRequest;
import com.example.feat1.DDD.order_context.domain.model.CartStatus;
import com.example.feat1.DDD.order_context.domain.model.OrderDomainException;
import com.example.feat1.DDD.order_context.domain.port.MenuQuotePort;
import com.example.feat1.DDD.order_context.domain.port.TableSessionValidationPort;
import com.example.feat1.DDD.order_context.domain.port.TableValidationPort;
import com.example.feat1.DDD.order_context.domain.snapshot.OrderMenuQuote;
import com.example.feat1.DDD.order_context.domain.snapshot.OrderTableSnapshot;
import com.example.feat1.DDD.order_context.domain.snapshot.OrderToppingSnapshot;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderCartEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderCartLineEntity;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderCartLineRepository;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderCartRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CartServiceTest {
  private OrderCartRepository cartRepository;
  private OrderCartLineRepository lineRepository;
  private MenuQuotePort menuQuotePort;
  private TableValidationPort tableValidationPort;
  private TableSessionValidationPort tableSessionValidationPort;
  private CartService service;

  private final UUID userId = UUID.randomUUID();
  private final UUID cartId = UUID.randomUUID();
  private final UUID tableId = UUID.randomUUID();
  private final UUID dishId = UUID.randomUUID();
  private final UUID toppingA = UUID.randomUUID();
  private final UUID toppingB = UUID.randomUUID();
  private final AtomicReference<OrderCartLineEntity> savedLine = new AtomicReference<>();

  @BeforeEach
  void setUp() {
    cartRepository = mock(OrderCartRepository.class);
    lineRepository = mock(OrderCartLineRepository.class);
    menuQuotePort = mock(MenuQuotePort.class);
    tableValidationPort = mock(TableValidationPort.class);
    tableSessionValidationPort = mock(TableSessionValidationPort.class);
    service =
        new CartService(
            cartRepository,
            lineRepository,
            menuQuotePort,
            tableValidationPort,
            tableSessionValidationPort);

    OrderCartEntity cart = new OrderCartEntity();
    cart.setId(cartId);
    cart.setUserId(userId);
    cart.setStatus(CartStatus.ACTIVE);
    when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
    when(cartRepository.save(any(OrderCartEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(lineRepository.save(any(OrderCartLineEntity.class)))
        .thenAnswer(
            invocation -> {
              OrderCartLineEntity line = invocation.getArgument(0);
              if (line.getId() == null) {
                line.setId(UUID.randomUUID());
              }
              savedLine.set(line);
              return line;
            });
    when(lineRepository.findByCart_IdOrderByIdAsc(cartId))
        .thenAnswer(invocation -> savedLine.get() == null ? List.of() : List.of(savedLine.get()));
    when(lineRepository.findByCart_IdAndDishIdAndToppingKey(any(), any(), any()))
        .thenAnswer(
            invocation ->
                savedLine.get() == null ? Optional.empty() : Optional.of(savedLine.get()));

    when(tableValidationPort.validate(any(UUID.class)))
        .thenAnswer(
            invocation ->
                new OrderTableSnapshot(
                    invocation.getArgument(0), "A01", "Table A01", UUID.randomUUID(), "Main Hall"));
    when(menuQuotePort.quote(any(), any()))
        .thenReturn(
            new OrderMenuQuote(
                dishId,
                "Pho Bo",
                BigDecimal.valueOf(65000),
                List.of(
                    new OrderToppingSnapshot(
                        UUID.randomUUID(),
                        "Herbs",
                        toppingA,
                        "Bean sprouts",
                        BigDecimal.valueOf(5000)),
                    new OrderToppingSnapshot(
                        UUID.randomUUID(), "Sauce", toppingB, "Chili", BigDecimal.valueOf(3000))),
                BigDecimal.valueOf(8000),
                BigDecimal.valueOf(73000)));
  }

  @Test
  void addItemMergesByDishAndSortedToppings() {
    var first =
        service.addItem(
            userId, new AddCartItemRequest(tableId, null, dishId, List.of(toppingB, toppingA), 2));
    var second =
        service.addItem(
            userId, new AddCartItemRequest(tableId, null, dishId, List.of(toppingA, toppingB), 3));

    assertThat(first.lines()).hasSize(1);
    assertThat(second.lines()).hasSize(1);
    assertThat(second.lines().get(0).quantity()).isEqualTo(5);
    assertThat(second.lines().get(0).unitPrice()).isEqualByComparingTo("73000");
    assertThat(second.total()).isEqualByComparingTo("365000");
  }

  @Test
  void addItemRejectsInvalidQuantityWithStableCode() {
    assertThatThrownBy(
            () ->
                service.addItem(
                    userId, new AddCartItemRequest(tableId, null, dishId, List.of(), 0)))
        .isInstanceOf(OrderDomainException.class)
        .extracting("code")
        .isEqualTo(OrderDomainException.QUANTITY_INVALID);
  }

  @Test
  void cartCannotSwitchTableUntilCleared() {
    service.addItem(userId, new AddCartItemRequest(tableId, null, dishId, List.of(), 1));

    assertThatThrownBy(
            () ->
                service.addItem(
                    userId, new AddCartItemRequest(UUID.randomUUID(), null, dishId, List.of(), 1)))
        .isInstanceOf(OrderDomainException.class)
        .extracting("code")
        .isEqualTo(OrderDomainException.CART_TABLE_MISMATCH);
  }
}
