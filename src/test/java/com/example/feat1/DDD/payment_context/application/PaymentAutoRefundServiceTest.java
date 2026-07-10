package com.example.feat1.DDD.payment_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.order_context.application.event.OrderCancelledEvent;
import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.RecordRefundRequest;
import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.RefundResponse;
import com.example.feat1.DDD.payment_context.infrastructure.entity.PaymentEntity;
import com.example.feat1.DDD.payment_context.infrastructure.entity.PaymentRefundEntity;
import com.example.feat1.DDD.payment_context.infrastructure.repository.PaymentProcessedEventRepository;
import com.example.feat1.DDD.payment_context.infrastructure.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaymentAutoRefundServiceTest {

  private static final String CONSUMER_NAME = "payment-order-cancelled";

  private PaymentProcessedEventRepository processedEventRepository;
  private PaymentRepository paymentRepository;
  private PaymentService paymentService;
  private PaymentAutoRefundService autoRefundService;

  private final UUID orderId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    processedEventRepository = mock(PaymentProcessedEventRepository.class);
    paymentRepository = mock(PaymentRepository.class);
    paymentService = mock(PaymentService.class);
    autoRefundService =
        new PaymentAutoRefundService(processedEventRepository, paymentRepository, paymentService);
  }

  private OrderCancelledEvent event(UUID eventId, boolean wholeOrder) {
    return new OrderCancelledEvent(
        eventId, OrderCancelledEvent.TYPE, Instant.now(), orderId, wholeOrder, List.of(), 1);
  }

  private PaymentEntity payment(BigDecimal amount, BigDecimal... refundAmounts) {
    PaymentEntity payment = new PaymentEntity();
    payment.setId(UUID.randomUUID());
    payment.setOrderId(orderId);
    payment.setAmount(amount);
    List<PaymentRefundEntity> refunds = new ArrayList<>();
    for (BigDecimal refundAmount : refundAmounts) {
      PaymentRefundEntity refund = new PaymentRefundEntity();
      refund.setAmount(refundAmount);
      refunds.add(refund);
    }
    payment.setRefunds(refunds);
    return payment;
  }

  @Test
  void partialCancelTriggersNoRefund() {
    UUID eventId = UUID.randomUUID();
    when(processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME))
        .thenReturn(false);

    autoRefundService.onOrderCancelled(event(eventId, false));

    verify(paymentService, never()).recordRefund(any(), any(), any());
    verify(paymentRepository, never()).findByOrderIdOrderByCreatedAtAsc(any());
    verify(processedEventRepository, never()).save(any());
  }

  @Test
  void wholeOrderCancelRefundsUnrefundedRemainderPerPayment() {
    UUID eventId = UUID.randomUUID();
    when(processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME))
        .thenReturn(false);

    PaymentEntity fullyUnrefunded = payment(new BigDecimal("50.00"));
    PaymentEntity partiallyRefunded = payment(new BigDecimal("30.00"), new BigDecimal("10.00"));
    when(paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of(fullyUnrefunded, partiallyRefunded));
    when(paymentService.recordRefund(isNull(), any(), any()))
        .thenReturn(
            new RefundResponse(
                UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO, null, Instant.now()));

    autoRefundService.onOrderCancelled(event(eventId, true));

    ArgumentCaptor<RecordRefundRequest> captor1 =
        ArgumentCaptor.forClass(RecordRefundRequest.class);
    verify(paymentService).recordRefund(isNull(), eq(fullyUnrefunded.getId()), captor1.capture());
    assertThat(captor1.getValue().amount()).isEqualByComparingTo("50.00");
    assertThat(captor1.getValue().idempotencyKey())
        .isEqualTo("auto-cancel-" + orderId + "-" + fullyUnrefunded.getId());
    assertThat(captor1.getValue().reason()).isEqualTo("Order cancelled");

    ArgumentCaptor<RecordRefundRequest> captor2 =
        ArgumentCaptor.forClass(RecordRefundRequest.class);
    verify(paymentService).recordRefund(isNull(), eq(partiallyRefunded.getId()), captor2.capture());
    assertThat(captor2.getValue().amount()).isEqualByComparingTo("20.00");
    assertThat(captor2.getValue().idempotencyKey())
        .isEqualTo("auto-cancel-" + orderId + "-" + partiallyRefunded.getId());

    verify(processedEventRepository).save(any());
  }

  @Test
  void alreadyFullyRefundedPaymentIsSkipped() {
    UUID eventId = UUID.randomUUID();
    when(processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME))
        .thenReturn(false);

    PaymentEntity fullyRefunded = payment(new BigDecimal("40.00"), new BigDecimal("40.00"));
    when(paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of(fullyRefunded));

    autoRefundService.onOrderCancelled(event(eventId, true));

    verify(paymentService, never()).recordRefund(any(), any(), any());
    verify(processedEventRepository).save(any());
  }

  @Test
  void replayOfAlreadyProcessedEventTriggersNoRefund() {
    UUID eventId = UUID.randomUUID();
    when(processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME))
        .thenReturn(true);

    autoRefundService.onOrderCancelled(event(eventId, true));

    verify(paymentService, never()).recordRefund(any(), any(), any());
    verify(paymentRepository, never()).findByOrderIdOrderByCreatedAtAsc(any());
    verify(processedEventRepository, never()).save(any());
  }

  @Test
  void noPaymentsForOrderIsNoOpButStillRecordsLedger() {
    UUID eventId = UUID.randomUUID();
    when(processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME))
        .thenReturn(false);
    when(paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());

    autoRefundService.onOrderCancelled(event(eventId, true));

    verify(paymentService, never()).recordRefund(any(), any(), any());
    verify(processedEventRepository, times(1)).save(any());
    verifyNoMoreInteractions(paymentService);
  }
}
