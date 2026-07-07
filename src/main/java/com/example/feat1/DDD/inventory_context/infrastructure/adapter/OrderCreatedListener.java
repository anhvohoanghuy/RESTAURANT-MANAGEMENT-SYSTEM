package com.example.feat1.DDD.inventory_context.infrastructure.adapter;

import com.example.feat1.DDD.inventory_context.application.InventoryReservationService;
import com.example.feat1.DDD.order_context.application.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin Kafka adapter for the order-confirmation saga: consumes {@code OrderCreated} from {@code
 * orders.created} and delegates to {@link InventoryReservationService#onOrderCreated} — no business
 * logic lives here (RESEARCH anti-pattern). All reservation, idempotency, and result-publishing
 * logic is in the {@code @Transactional} service.
 */
@Component
@RequiredArgsConstructor
public class OrderCreatedListener {
  private final InventoryReservationService reservationService;

  @KafkaListener(
      topics = "${order.events.order-created-topic:orders.created}",
      groupId = "${inventory.order-created.consumer.group-id:inventory-order-created}",
      containerFactory = "orderCreatedKafkaListenerContainerFactory")
  public void onOrderCreated(OrderCreatedEvent event) {
    reservationService.onOrderCreated(event);
  }
}
