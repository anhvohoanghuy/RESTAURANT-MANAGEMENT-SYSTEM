package com.example.feat1.DDD.table_context.infrastructure.adapter;

import com.example.feat1.DDD.table_context.application.event.TableOperationEvent;
import com.example.feat1.DDD.table_context.domain.port.TableOperationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaTableOperationEventPublisher implements TableOperationEventPublisher {
  private final KafkaTemplate<String, TableOperationEvent> tableOperationEventKafkaTemplate;

  @Value("${table.events.topic:tables.events}")
  private String tableEventsTopic;

  @Override
  public void publish(TableOperationEvent event) {
    tableOperationEventKafkaTemplate.send(tableEventsTopic, event.tableId().toString(), event);
  }
}
