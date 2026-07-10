package com.example.feat1.DDD.order_context.domain.port;

import com.example.feat1.DDD.order_context.domain.model.KitchenItemStatusView;
import java.util.Map;
import java.util.UUID;

/**
 * Cross-context read port that lets order_context synchronously ask kitchen_context "where is each
 * of this order's kitchen items in the fulfillment lifecycle right now" without importing
 * kitchen_context's JPA internals or trusting the eventually-consistent {@code OrderEntity.status}
 * projection. The consuming context (order) owns this port + view type; the adapter lives in
 * kitchen_context, mirroring the {@code OrderLineLookupPort}/{@code OrderLineLookupAdapter}
 * convention in the opposite direction.
 *
 * <p>Lines with no kitchen ticket item yet (order not CONFIRMED) simply have no map entry — the
 * caller treats "absent" as before-PREPARING.
 */
public interface KitchenItemStatusPort {
  Map<UUID, KitchenItemStatusView> findStatuses(UUID orderId);
}
