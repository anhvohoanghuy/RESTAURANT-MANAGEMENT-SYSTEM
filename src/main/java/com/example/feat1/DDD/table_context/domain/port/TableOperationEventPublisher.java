package com.example.feat1.DDD.table_context.domain.port;

import com.example.feat1.DDD.table_context.application.event.TableOperationEvent;

public interface TableOperationEventPublisher {
  void publish(TableOperationEvent event);
}
