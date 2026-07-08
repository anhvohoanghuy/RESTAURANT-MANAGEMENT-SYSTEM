package com.example.feat1.DDD.inventory_context.infrastructure.adapter;

import com.example.feat1.DDD.inventory_context.application.InventoryReservationSettlementService;
import com.example.feat1.DDD.inventory_context.application.event.SettleTriggerEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin Kafka adapter for the settlement saga: consumes {@code SettleTrigger} from the
 * settle-trigger topic and delegates to {@link
 * InventoryReservationSettlementService#onSettleTrigger} — no business logic lives here (RESEARCH
 * anti-pattern). All idempotency, recipe re-resolution, stock deduction, and reservation-completion
 * logic is in the {@code @Transactional} service; a missing reservation/order-line throws there so
 * the container retries then routes to the DLT (D-01/D-05).
 */
@Component
@RequiredArgsConstructor
public class SettleTriggerListener {
  private final InventoryReservationSettlementService settlementService;

  @KafkaListener(
      topics = "${kitchen.events.settle-trigger-topic:kitchen.settlement-trigger}",
      groupId = "${inventory.settlement.consumer.group-id:inventory-settlement}",
      containerFactory = "settleTriggerKafkaListenerContainerFactory")
  public void onSettleTrigger(SettleTriggerEvent event) {
    settlementService.onSettleTrigger(event);
  }
}
