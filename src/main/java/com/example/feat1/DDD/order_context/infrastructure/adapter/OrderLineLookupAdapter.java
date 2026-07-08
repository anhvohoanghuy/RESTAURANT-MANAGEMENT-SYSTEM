package com.example.feat1.DDD.order_context.infrastructure.adapter;

import com.example.feat1.DDD.inventory_context.domain.port.OrderLineLookupPort;
import com.example.feat1.DDD.inventory_context.domain.snapshot.OrderLineRecipeSnapshot;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderLineToppingSnapshot;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderLineRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owning-context adapter for {@link OrderLineLookupPort}. Reads a single order line keyed by BOTH
 * orderId and orderLineId (cross-order collision defense) and maps it to the narrow {@link
 * OrderLineRecipeSnapshot}, never exposing the full OrderLineEntity/OrderEntity graph.
 */
@Component
@RequiredArgsConstructor
public class OrderLineLookupAdapter implements OrderLineLookupPort {
  private final OrderLineRepository orderLineRepository;

  @Override
  @Transactional(readOnly = true)
  public Optional<OrderLineRecipeSnapshot> findLine(UUID orderId, UUID orderLineId) {
    return orderLineRepository
        .findByOrder_IdAndId(orderId, orderLineId)
        .map(
            line ->
                new OrderLineRecipeSnapshot(
                    line.getId(),
                    line.getDishId(),
                    line.getQuantity(),
                    line.getSelectedToppings().stream()
                        .map(OrderLineToppingSnapshot::getToppingOptionId)
                        .toList()));
  }
}
