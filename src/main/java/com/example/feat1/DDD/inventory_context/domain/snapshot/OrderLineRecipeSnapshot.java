package com.example.feat1.DDD.inventory_context.domain.snapshot;

import java.util.List;
import java.util.UUID;

/**
 * Narrow cross-context read view of a single order line's recipe-relevant fields. Exposes only what
 * inventory needs to re-resolve a recipe (dish + selected toppings), never the full
 * OrderLineEntity/OrderEntity graph (Information-Disclosure mitigation).
 */
public record OrderLineRecipeSnapshot(
    UUID orderLineId, UUID dishId, int quantity, List<UUID> selectedToppingOptionIds) {}
