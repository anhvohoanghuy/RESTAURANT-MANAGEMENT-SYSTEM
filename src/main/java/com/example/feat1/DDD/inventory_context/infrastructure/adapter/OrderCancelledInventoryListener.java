package com.example.feat1.DDD.inventory_context.infrastructure.adapter;

import com.example.feat1.DDD.inventory_context.application.InventoryReservationReleaseService;
import com.example.feat1.DDD.order_context.application.event.OrderCancelledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin Kafka adapter for the reservation-release compensation (CANCEL-05): consumes {@code
 * OrderCancelled} from the {@code orders.cancelled} topic and delegates to {@link
 * InventoryReservationReleaseService#onOrderCancelled} — no business logic lives here (RESEARCH
 * anti-pattern, mirrors {@link SettleTriggerListener}). All idempotency, recipe re-resolution,
 * reserved-quantity release, and reservation-completion logic is in the {@code @Transactional}
 * service; a missing reservation/order-line throws there so the container retries then routes to
 * the DLT.
 */
@Component
@RequiredArgsConstructor
public class OrderCancelledInventoryListener {
  private final InventoryReservationReleaseService releaseService;

  @KafkaListener(
      topics = "${order.events.order-cancelled-topic:orders.cancelled}",
      groupId = "${inventory.release.consumer.group-id:inventory-release}",
      containerFactory = "orderCancelledInventoryKafkaListenerContainerFactory")
  public void onOrderCancelled(OrderCancelledEvent event) {
    releaseService.onOrderCancelled(event);
  }
}
