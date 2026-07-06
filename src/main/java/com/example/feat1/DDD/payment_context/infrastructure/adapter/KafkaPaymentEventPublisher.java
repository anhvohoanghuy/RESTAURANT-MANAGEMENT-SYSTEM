package com.example.feat1.DDD.payment_context.infrastructure.adapter;

import com.example.feat1.DDD.payment_context.application.event.PaymentEvent;
import com.example.feat1.DDD.payment_context.domain.port.PaymentEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {
  private final KafkaTemplate<String, PaymentEvent> paymentEventKafkaTemplate;

  @Value("${payment.events.topic:payments.events}")
  private String paymentEventsTopic;

  @Override
  public void publish(PaymentEvent event) {
    paymentEventKafkaTemplate.send(paymentEventsTopic, event.orderId().toString(), event);
  }
}
