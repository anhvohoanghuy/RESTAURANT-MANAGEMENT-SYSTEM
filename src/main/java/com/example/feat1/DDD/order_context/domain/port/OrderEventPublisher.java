package com.example.feat1.DDD.order_context.domain.port;

import com.example.feat1.DDD.order_context.application.event.OrderCreatedEvent;

public interface OrderEventPublisher {
  void publishOrderCreated(OrderCreatedEvent event);
}
