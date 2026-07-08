package com.example.feat1.DDD.kitchen_context.infrastructure.adapter;

import com.example.feat1.DDD.inventory_context.application.event.SettleTriggerEvent;
import com.example.feat1.DDD.kitchen_context.domain.port.KitchenSettleTriggerPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaKitchenSettleTriggerPublisher implements KitchenSettleTriggerPublisher {
  private final KafkaTemplate<String, SettleTriggerEvent> settleTriggerKafkaTemplate;

  @Value("${kitchen.events.settle-trigger-topic:kitchen.settlement-trigger}")
  private String settleTriggerTopic;

  @Override
  public void publishSettleTrigger(SettleTriggerEvent event) {
    settleTriggerKafkaTemplate.send(settleTriggerTopic, event.orderId().toString(), event);
  }
}
