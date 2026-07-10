package com.example.feat1.DDD.payment_context.application;

import com.example.feat1.DDD.order_context.application.event.OrderCancelledEvent;
import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.RecordRefundRequest;
import com.example.feat1.DDD.payment_context.infrastructure.entity.PaymentEntity;
import com.example.feat1.DDD.payment_context.infrastructure.entity.PaymentProcessedEventEntity;
import com.example.feat1.DDD.payment_context.infrastructure.entity.PaymentRefundEntity;
import com.example.feat1.DDD.payment_context.infrastructure.repository.PaymentProcessedEventRepository;
import com.example.feat1.DDD.payment_context.infrastructure.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment's first-ever Kafka-driven consumer: reacts to a whole-order {@link OrderCancelledEvent}
 * by refunding the unrefunded remainder of every payment on the order, reusing {@link
 * PaymentService#recordRefund} (inheriting its overpay guard and per-payment idempotency-key dedup)
 * with a null system actor (D-A5, no fabricated user). Partial-cancel events ({@code
 * wholeOrder=false}) trigger no refund (D-6). Idempotent via the new {@code
 * payment_processed_events} ledger, mirroring the {@link
 * com.example.feat1.DDD.order_context.application.OrderConfirmationService} pre-check +
 * business-logic + ledger-last idiom.
 */
@Service
@RequiredArgsConstructor
public class PaymentAutoRefundService {

  /** Ledger consumer identity for the Payment-side order-cancelled handler. */
  static final String CONSUMER_NAME = "payment-order-cancelled";

  private final PaymentProcessedEventRepository processedEventRepository;
  private final PaymentRepository paymentRepository;
  private final PaymentService paymentService;

  @Transactional
  public void onOrderCancelled(OrderCancelledEvent event) {
    // (1) Idempotency fast pre-check: absorb an already-recorded replay cheaply. The authoritative
    // guard is the ledger insert at the END of this method.
    if (processedEventRepository.existsByEventIdAndConsumerName(event.eventId(), CONSUMER_NAME)) {
      return;
    }

    // (2) Hard gate: partial-cancel events trigger no refund (D-6).
    if (!event.wholeOrder()) {
      return;
    }

    // (3) Refund the unrefunded remainder of every payment on the order, reusing the existing
    // recordRefund business call (do not duplicate its overpay guard or write PaymentRefundEntity
    // directly).
    List<PaymentEntity> payments =
        paymentRepository.findByOrderIdOrderByCreatedAtAsc(event.orderId());
    for (PaymentEntity payment : payments) {
      refundRemainder(event.orderId(), payment);
    }

    // (4) Record the idempotency-ledger row LAST, in THIS transaction, so it commits atomically
    // with any refunds issued above (mirrors OrderConfirmationService's CR-01/I-WR-01 fix).
    recordProcessed(event.eventId());
  }

  private void refundRemainder(UUID orderId, PaymentEntity payment) {
    BigDecimal alreadyRefunded =
        payment.getRefunds().stream()
            .map(PaymentRefundEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal remaining = payment.getAmount().subtract(alreadyRefunded);
    if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
      return;
    }
    String idempotencyKey = "auto-cancel-" + orderId + "-" + payment.getId();
    paymentService.recordRefund(
        null,
        payment.getId(),
        new RecordRefundRequest(remaining, idempotencyKey, "Order cancelled"));
  }

  private void recordProcessed(UUID eventId) {
    PaymentProcessedEventEntity ledger = new PaymentProcessedEventEntity();
    ledger.setEventId(eventId);
    ledger.setConsumerName(CONSUMER_NAME);
    ledger.setProcessedAt(Instant.now());
    processedEventRepository.save(ledger);
  }
}
