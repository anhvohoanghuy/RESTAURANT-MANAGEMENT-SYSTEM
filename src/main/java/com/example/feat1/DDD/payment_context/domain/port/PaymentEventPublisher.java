package com.example.feat1.DDD.payment_context.domain.port;

import com.example.feat1.DDD.payment_context.application.event.PaymentEvent;

public interface PaymentEventPublisher {
  void publish(PaymentEvent event);
}
