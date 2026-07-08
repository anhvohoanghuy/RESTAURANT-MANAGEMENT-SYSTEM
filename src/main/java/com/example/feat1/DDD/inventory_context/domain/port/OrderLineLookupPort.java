package com.example.feat1.DDD.inventory_context.domain.port;

import com.example.feat1.DDD.inventory_context.domain.snapshot.OrderLineRecipeSnapshot;
import java.util.Optional;
import java.util.UUID;

/**
 * Cross-context read port that lets inventory_context re-resolve one order line's recipe by
 * (orderId, orderLineId) without touching order_context JPA directly. The consuming context
 * (inventory) owns this port + snapshot; the adapter lives in order_context, mirroring the
 * MenuRecipeCostingPort/MenuRecipeCostingAdapter convention.
 */
public interface OrderLineLookupPort {
  Optional<OrderLineRecipeSnapshot> findLine(UUID orderId, UUID orderLineId);
}
