package com.example.feat1.DDD.payment_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.RecordPaymentRequest;
import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.RecordRefundRequest;
import com.example.feat1.DDD.payment_context.domain.model.PaymentDomainException;
import com.example.feat1.DDD.payment_context.domain.model.PaymentMethod;
import com.example.feat1.DDD.payment_context.domain.model.PaymentRecordStatus;
import com.example.feat1.DDD.payment_context.domain.port.OrderPaymentLookupPort;
import com.example.feat1.DDD.payment_context.domain.port.PaymentEventPublisher;
import com.example.feat1.DDD.payment_context.domain.snapshot.PaymentOrderSnapshot;
import com.example.feat1.DDD.payment_context.infrastructure.entity.PaymentEntity;
import com.example.feat1.DDD.payment_context.infrastructure.entity.PaymentRefundEntity;
import com.example.feat1.DDD.payment_context.infrastructure.repository.PaymentRefundRepository;
import com.example.feat1.DDD.payment_context.infrastructure.repository.PaymentRepository;
import com.example.feat1.DDD.payment_context.infrastructure.repository.PaymentRequestRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PaymentServiceTest {
  private PaymentRepository paymentRepository;
  private PaymentRefundRepository refundRepository;
  private PaymentRequestRepository paymentRequestRepository;
  private OrderPaymentLookupPort orderPaymentLookupPort;
  private PaymentEventPublisher paymentEventPublisher;
  private PaymentService paymentService;

  private final UUID orderId = UUID.randomUUID();
  private final UUID orderUserId = UUID.randomUUID();
  private final UUID actorUserId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    paymentRepository = mock(PaymentRepository.class);
    refundRepository = mock(PaymentRefundRepository.class);
    paymentRequestRepository = mock(PaymentRequestRepository.class);
    orderPaymentLookupPort = mock(OrderPaymentLookupPort.class);
    paymentEventPublisher = mock(PaymentEventPublisher.class);
    paymentService =
        new PaymentService(
            paymentRepository,
            refundRepository,
            paymentRequestRepository,
            orderPaymentLookupPort,
            paymentEventPublisher);
    ReflectionTestUtils.setField(paymentService, "qrPlaceholderBaseUrl", "http://pay.local/qr");

    when(orderPaymentLookupPort.getSubmittedOrder(orderId))
        .thenReturn(new PaymentOrderSnapshot(orderId, orderUserId, BigDecimal.valueOf(100000)));
    when(paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenAnswer(
            invocation -> {
              PaymentEntity payment = invocation.getArgument(0);
              payment.setId(UUID.randomUUID());
              return payment;
            });
  }

  @Test
  void recordPaymentReturnsExistingPaymentForSameIdempotencyKey() {
    PaymentEntity existing = payment(BigDecimal.valueOf(30000));
    when(paymentRepository.findByOrderIdAndIdempotencyKey(orderId, "same-key"))
        .thenReturn(Optional.of(existing));

    var response =
        paymentService.recordPayment(
            actorUserId,
            orderId,
            new RecordPaymentRequest(
                BigDecimal.valueOf(30000), PaymentMethod.CASH, "same-key", null, null));

    assertThat(response.paymentId()).isEqualTo(existing.getId());
    verify(paymentRepository, never()).save(any(PaymentEntity.class));
    verify(paymentEventPublisher, never()).publish(any());
  }

  @Test
  void recordPaymentRejectsOverpay() {
    when(paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of(payment(BigDecimal.valueOf(90000))));

    assertThatThrownBy(
            () ->
                paymentService.recordPayment(
                    actorUserId,
                    orderId,
                    new RecordPaymentRequest(
                        BigDecimal.valueOf(20000),
                        PaymentMethod.BANK_TRANSFER,
                        "overpay",
                        null,
                        null)))
        .isInstanceOf(PaymentDomainException.class)
        .extracting("code")
        .isEqualTo(PaymentDomainException.OVERPAY_NOT_ALLOWED);
  }

  @Test
  void summarizeUsesPaidAmountForPaymentStatusAndRefundStatusSeparately() {
    PaymentEntity payment = payment(BigDecimal.valueOf(100000));
    payment.getRefunds().add(refund(payment, BigDecimal.valueOf(20000)));
    when(paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of(payment));

    var summary = paymentService.summarize(orderId, BigDecimal.valueOf(100000));

    assertThat(summary.paymentStatus()).isEqualTo("PAID");
    assertThat(summary.refundStatus()).isEqualTo("PARTIALLY_REFUNDED");
    assertThat(summary.refundedAmount()).isEqualByComparingTo("20000");
  }

  @Test
  void recordRefundRejectsRefundsBeyondOriginalPaymentAmount() {
    PaymentEntity payment = payment(BigDecimal.valueOf(100000));
    payment.getRefunds().add(refund(payment, BigDecimal.valueOf(90000)));
    when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

    assertThatThrownBy(
            () ->
                paymentService.recordRefund(
                    actorUserId,
                    payment.getId(),
                    new RecordRefundRequest(BigDecimal.valueOf(20000), "refund-overflow", null)))
        .isInstanceOf(PaymentDomainException.class)
        .extracting("code")
        .isEqualTo(PaymentDomainException.REFUND_EXCEEDS_PAYMENT);
  }

  private PaymentEntity payment(BigDecimal amount) {
    PaymentEntity payment = new PaymentEntity();
    payment.setId(UUID.randomUUID());
    payment.setOrderId(orderId);
    payment.setOrderUserId(orderUserId);
    payment.setAmount(amount);
    payment.setMethod(PaymentMethod.CASH);
    payment.setStatus(PaymentRecordStatus.CONFIRMED);
    payment.setIdempotencyKey("key-" + UUID.randomUUID());
    payment.setActorUserId(actorUserId);
    payment.setCreatedAt(Instant.now());
    return payment;
  }

  private PaymentRefundEntity refund(PaymentEntity payment, BigDecimal amount) {
    PaymentRefundEntity refund = new PaymentRefundEntity();
    refund.setId(UUID.randomUUID());
    refund.setPayment(payment);
    refund.setAmount(amount);
    refund.setIdempotencyKey("refund-" + UUID.randomUUID());
    refund.setActorUserId(actorUserId);
    refund.setCreatedAt(Instant.now());
    return refund;
  }
}
