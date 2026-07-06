package com.example.feat1.DDD.payment_context.infrastructure.adapter;

import com.example.feat1.DDD.order_context.infrastructure.repository.OrderRepository;
import com.example.feat1.DDD.payment_context.domain.model.PaymentDomainException;
import com.example.feat1.DDD.payment_context.domain.port.OrderPaymentLookupPort;
import com.example.feat1.DDD.payment_context.domain.snapshot.PaymentOrderSnapshot;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderPaymentLookupAdapter implements OrderPaymentLookupPort {
  private final OrderRepository orderRepository;

  @Override
  public PaymentOrderSnapshot getSubmittedOrder(UUID orderId) {
    return orderRepository
        .findById(orderId)
        .map(order -> new PaymentOrderSnapshot(order.getId(), order.getUserId(), order.getTotal()))
        .orElseThrow(PaymentDomainException::orderNotFound);
  }
}
