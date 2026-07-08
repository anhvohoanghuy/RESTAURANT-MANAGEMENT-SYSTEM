package com.example.feat1.DDD.order_context.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.inventory_context.domain.snapshot.OrderLineRecipeSnapshot;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderLineEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderLineToppingSnapshot;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderLineRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderLineLookupAdapterTest {
  private OrderLineRepository orderLineRepository;
  private OrderLineLookupAdapter adapter;

  @BeforeEach
  void setUp() {
    orderLineRepository = mock(OrderLineRepository.class);
    adapter = new OrderLineLookupAdapter(orderLineRepository);
  }

  @Test
  void mapsEntityToNarrowSnapshotKeyedByBothIds() {
    UUID orderId = UUID.randomUUID();
    UUID orderLineId = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    UUID toppingOptionA = UUID.randomUUID();
    UUID toppingOptionB = UUID.randomUUID();

    OrderLineEntity entity =
        line(orderLineId, dishId, 3, topping(toppingOptionA), topping(toppingOptionB));
    when(orderLineRepository.findByOrder_IdAndId(orderId, orderLineId))
        .thenReturn(Optional.of(entity));

    Optional<OrderLineRecipeSnapshot> result = adapter.findLine(orderId, orderLineId);

    // Delegates keyed by BOTH ids (cross-order collision defense).
    verify(orderLineRepository).findByOrder_IdAndId(orderId, orderLineId);
    assertThat(result).isPresent();
    OrderLineRecipeSnapshot snapshot = result.get();
    assertThat(snapshot.orderLineId()).isEqualTo(orderLineId);
    assertThat(snapshot.dishId()).isEqualTo(dishId);
    assertThat(snapshot.quantity()).isEqualTo(3);
    assertThat(snapshot.selectedToppingOptionIds()).containsExactly(toppingOptionA, toppingOptionB);
  }

  @Test
  void returnsEmptyWhenLineNotFound() {
    UUID orderId = UUID.randomUUID();
    UUID orderLineId = UUID.randomUUID();
    when(orderLineRepository.findByOrder_IdAndId(orderId, orderLineId))
        .thenReturn(Optional.empty());

    Optional<OrderLineRecipeSnapshot> result = adapter.findLine(orderId, orderLineId);

    assertThat(result).isEmpty();
  }

  // ---- fixtures -------------------------------------------------------------

  private OrderLineEntity line(
      UUID id, UUID dishId, int quantity, OrderLineToppingSnapshot... toppings) {
    OrderLineEntity entity = new OrderLineEntity();
    entity.setId(id);
    entity.setDishId(dishId);
    entity.setQuantity(quantity);
    entity.setSelectedToppings(List.of(toppings));
    return entity;
  }

  private OrderLineToppingSnapshot topping(UUID toppingOptionId) {
    OrderLineToppingSnapshot topping = new OrderLineToppingSnapshot();
    topping.setToppingGroupId(UUID.randomUUID());
    topping.setToppingGroupName("group");
    topping.setToppingOptionId(toppingOptionId);
    topping.setToppingOptionName("option");
    topping.setAdditionalPrice(BigDecimal.ZERO);
    return topping;
  }
}
