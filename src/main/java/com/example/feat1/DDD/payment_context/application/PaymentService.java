package com.example.feat1.DDD.payment_context.application;

import com.example.feat1.DDD.order_context.domain.port.PaymentSummaryPort;
import com.example.feat1.DDD.order_context.domain.snapshot.OrderPaymentSummary;
import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.CreateQrPaymentRequest;
import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.PaymentHistoryResponse;
import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.PaymentResponse;
import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.PaymentSummaryResponse;
import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.QrPaymentRequestResponse;
import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.RecordPaymentRequest;
import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.RecordRefundRequest;
import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.RefundResponse;
import com.example.feat1.DDD.payment_context.application.event.PaymentEvent;
import com.example.feat1.DDD.payment_context.domain.model.PaymentDomainException;
import com.example.feat1.DDD.payment_context.domain.model.PaymentMethod;
import com.example.feat1.DDD.payment_context.domain.model.PaymentRecordStatus;
import com.example.feat1.DDD.payment_context.domain.model.PaymentRequestStatus;
import com.example.feat1.DDD.payment_context.domain.model.PaymentStatus;
import com.example.feat1.DDD.payment_context.domain.model.RefundStatus;
import com.example.feat1.DDD.payment_context.domain.port.OrderPaymentLookupPort;
import com.example.feat1.DDD.payment_context.domain.port.PaymentEventPublisher;
import com.example.feat1.DDD.payment_context.domain.snapshot.PaymentOrderSnapshot;
import com.example.feat1.DDD.payment_context.infrastructure.entity.PaymentEntity;
import com.example.feat1.DDD.payment_context.infrastructure.entity.PaymentRefundEntity;
import com.example.feat1.DDD.payment_context.infrastructure.entity.PaymentRequestEntity;
import com.example.feat1.DDD.payment_context.infrastructure.repository.PaymentRefundRepository;
import com.example.feat1.DDD.payment_context.infrastructure.repository.PaymentRepository;
import com.example.feat1.DDD.payment_context.infrastructure.repository.PaymentRequestRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class PaymentService implements PaymentSummaryPort {
  private final PaymentRepository paymentRepository;
  private final PaymentRefundRepository refundRepository;
  private final PaymentRequestRepository paymentRequestRepository;
  private final OrderPaymentLookupPort orderPaymentLookupPort;
  private final PaymentEventPublisher paymentEventPublisher;

  @Value("${payment.qr.placeholder-base-url:http://localhost/payments/qr}")
  private String qrPlaceholderBaseUrl;

  @Transactional
  public PaymentResponse recordPayment(
      UUID actorUserId, UUID orderId, RecordPaymentRequest request) {
    requireIdempotencyKey(request.idempotencyKey());
    validatePositive(request.amount());
    PaymentMethod method = Optional.ofNullable(request.method()).orElse(PaymentMethod.CASH);

    Optional<PaymentEntity> existing =
        paymentRepository.findByOrderIdAndIdempotencyKey(orderId, request.idempotencyKey());
    if (existing.isPresent()) {
      return toResponse(existing.get());
    }

    PaymentOrderSnapshot order = orderPaymentLookupPort.getSubmittedOrder(orderId);
    PaymentSummaryTotals totals = summarizeTotals(orderId, order.total());
    if (totals.paidAmount().add(request.amount()).compareTo(order.total()) > 0) {
      throw PaymentDomainException.overpayNotAllowed();
    }

    PaymentEntity payment = new PaymentEntity();
    payment.setOrderId(order.orderId());
    payment.setOrderUserId(order.userId());
    payment.setAmount(request.amount());
    payment.setMethod(method);
    payment.setStatus(PaymentRecordStatus.CONFIRMED);
    payment.setIdempotencyKey(request.idempotencyKey().trim());
    payment.setReference(blankToNull(request.reference()));
    payment.setNote(blankToNull(request.note()));
    payment.setActorUserId(actorUserId);
    payment.setCreatedAt(Instant.now());

    PaymentEntity saved = paymentRepository.save(payment);
    publishAfterCommit(
        newEvent(PaymentEvent.PAYMENT_RECORDED, saved, null, saved.getAmount(), actorUserId));

    PaymentSummaryTotals afterTotals = summarizeTotals(orderId, order.total());
    if (afterTotals.paymentStatus() == PaymentStatus.PAID) {
      publishAfterCommit(
          newEvent(
              PaymentEvent.ORDER_PAYMENT_COMPLETED,
              saved,
              null,
              afterTotals.paidAmount(),
              actorUserId));
    }
    return toResponse(saved);
  }

  @Transactional
  public RefundResponse recordRefund(
      UUID actorUserId, UUID paymentId, RecordRefundRequest request) {
    requireIdempotencyKey(request.idempotencyKey());
    validateRefundPositive(request.amount());
    PaymentEntity payment =
        paymentRepository.findById(paymentId).orElseThrow(PaymentDomainException::paymentNotFound);

    Optional<PaymentRefundEntity> existing =
        refundRepository.findByPayment_IdAndIdempotencyKey(paymentId, request.idempotencyKey());
    if (existing.isPresent()) {
      return toRefundResponse(existing.get());
    }

    BigDecimal refunded =
        payment.getRefunds().stream()
            .map(PaymentRefundEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (refunded.add(request.amount()).compareTo(payment.getAmount()) > 0) {
      throw PaymentDomainException.refundExceedsPayment();
    }

    PaymentRefundEntity refund = new PaymentRefundEntity();
    refund.setPayment(payment);
    refund.setAmount(request.amount());
    refund.setIdempotencyKey(request.idempotencyKey().trim());
    refund.setReason(blankToNull(request.reason()));
    refund.setActorUserId(actorUserId);
    refund.setCreatedAt(Instant.now());
    PaymentRefundEntity saved = refundRepository.save(refund);
    payment.getRefunds().add(saved);

    publishAfterCommit(
        newEvent(
            PaymentEvent.PAYMENT_REFUNDED, payment, saved.getId(), saved.getAmount(), actorUserId));
    return toRefundResponse(saved);
  }

  @Transactional(readOnly = true)
  public List<PaymentResponse> getOrderPayments(UUID requesterUserId, UUID orderId) {
    PaymentOrderSnapshot order = orderPaymentLookupPort.getSubmittedOrder(orderId);
    if (!order.userId().equals(requesterUserId)) {
      throw PaymentDomainException.orderNotOwned();
    }
    return paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public QrPaymentRequestResponse createQrPaymentRequest(
      UUID userId, UUID orderId, CreateQrPaymentRequest request) {
    validatePositive(request.amount());
    PaymentOrderSnapshot order = orderPaymentLookupPort.getSubmittedOrder(orderId);
    if (!order.userId().equals(userId)) {
      throw PaymentDomainException.orderNotOwned();
    }
    PaymentSummaryTotals totals = summarizeTotals(orderId, order.total());
    if (request.amount().compareTo(totals.remainingAmount()) > 0) {
      throw PaymentDomainException.overpayNotAllowed();
    }

    PaymentRequestEntity entity = new PaymentRequestEntity();
    entity.setId(UUID.randomUUID());
    entity.setOrderId(orderId);
    entity.setOrderUserId(userId);
    entity.setAmount(request.amount());
    entity.setMethod(PaymentMethod.QR_CODE);
    entity.setStatus(PaymentRequestStatus.PENDING);
    entity.setCreatedAt(Instant.now());
    entity.setExpiresAt(entity.getCreatedAt().plusSeconds(15 * 60));
    entity.setPaymentUrl(qrPlaceholderBaseUrl + "/" + entity.getId());
    entity.setRedirectUrl(qrPlaceholderBaseUrl + "/" + entity.getId() + "/redirect");
    PaymentRequestEntity saved = paymentRequestRepository.save(entity);
    return toQrResponse(saved);
  }

  @Transactional(readOnly = true)
  public PaymentHistoryResponse listPayments(
      UUID orderId, UUID orderUserId, String cursor, int size) {
    int pageSize = Math.max(1, Math.min(size, 100));
    Instant cursorInstant = parseCursor(cursor);
    Specification<PaymentEntity> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (orderId != null) {
            predicates.add(cb.equal(root.get("orderId"), orderId));
          }
          if (orderUserId != null) {
            predicates.add(cb.equal(root.get("orderUserId"), orderUserId));
          }
          if (cursorInstant != null) {
            predicates.add(cb.lessThan(root.get("createdAt"), cursorInstant));
          }
          return cb.and(predicates.toArray(Predicate[]::new));
        };

    List<PaymentEntity> rows =
        paymentRepository
            .findAll(
                spec, PageRequest.of(0, pageSize + 1, Sort.by(Sort.Direction.DESC, "createdAt")))
            .getContent();
    boolean hasMore = rows.size() > pageSize;
    List<PaymentEntity> page = hasMore ? rows.subList(0, pageSize) : rows;
    String nextCursor = hasMore ? page.get(page.size() - 1).getCreatedAt().toString() : null;
    return new PaymentHistoryResponse(
        page.stream().map(this::toResponse).toList(), nextCursor, hasMore);
  }

  @Override
  @Transactional(readOnly = true)
  public OrderPaymentSummary summarize(UUID orderId, BigDecimal orderTotal) {
    PaymentSummaryTotals totals = summarizeTotals(orderId, orderTotal);
    return new OrderPaymentSummary(
        totals.paymentStatus().name(),
        totals.paidAmount(),
        totals.refundedAmount(),
        totals.refundStatus().name(),
        totals.remainingAmount());
  }

  @Transactional(readOnly = true)
  public PaymentSummaryResponse summarizePayment(UUID orderId, BigDecimal orderTotal) {
    PaymentSummaryTotals totals = summarizeTotals(orderId, orderTotal);
    return new PaymentSummaryResponse(
        totals.paymentStatus().name(),
        totals.paidAmount(),
        totals.refundedAmount(),
        totals.refundStatus().name(),
        totals.remainingAmount());
  }

  private PaymentSummaryTotals summarizeTotals(UUID orderId, BigDecimal orderTotal) {
    List<PaymentEntity> payments = paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
    BigDecimal paid =
        payments.stream().map(PaymentEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal refunded =
        payments.stream()
            .flatMap(payment -> payment.getRefunds().stream())
            .map(PaymentRefundEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal remaining = orderTotal.subtract(paid).max(BigDecimal.ZERO);
    PaymentStatus paymentStatus;
    if (paid.compareTo(BigDecimal.ZERO) <= 0) {
      paymentStatus = PaymentStatus.UNPAID;
    } else if (paid.compareTo(orderTotal) >= 0) {
      paymentStatus = PaymentStatus.PAID;
    } else {
      paymentStatus = PaymentStatus.PARTIALLY_PAID;
    }

    RefundStatus refundStatus;
    if (refunded.compareTo(BigDecimal.ZERO) <= 0) {
      refundStatus = RefundStatus.NONE;
    } else if (refunded.compareTo(paid) >= 0) {
      refundStatus = RefundStatus.REFUNDED;
    } else {
      refundStatus = RefundStatus.PARTIALLY_REFUNDED;
    }
    return new PaymentSummaryTotals(paymentStatus, paid, refunded, refundStatus, remaining);
  }

  private PaymentResponse toResponse(PaymentEntity payment) {
    List<RefundResponse> refunds =
        payment.getRefunds().stream()
            .sorted(Comparator.comparing(PaymentRefundEntity::getCreatedAt))
            .map(this::toRefundResponse)
            .toList();
    return new PaymentResponse(
        payment.getId(),
        payment.getOrderId(),
        payment.getOrderUserId(),
        payment.getAmount(),
        payment.getMethod(),
        payment.getStatus().name(),
        payment.getReference(),
        payment.getNote(),
        payment.getCreatedAt(),
        refunds);
  }

  private RefundResponse toRefundResponse(PaymentRefundEntity refund) {
    return new RefundResponse(
        refund.getId(),
        refund.getPayment().getId(),
        refund.getAmount(),
        refund.getReason(),
        refund.getCreatedAt());
  }

  private QrPaymentRequestResponse toQrResponse(PaymentRequestEntity entity) {
    return new QrPaymentRequestResponse(
        entity.getId(),
        entity.getOrderId(),
        entity.getAmount(),
        entity.getStatus(),
        entity.getPaymentUrl(),
        entity.getRedirectUrl(),
        entity.getCreatedAt(),
        entity.getExpiresAt());
  }

  private void validatePositive(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw PaymentDomainException.amountInvalid();
    }
  }

  private void validateRefundPositive(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw PaymentDomainException.refundAmountInvalid();
    }
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw PaymentDomainException.idempotencyKeyRequired();
    }
  }

  private Instant parseCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    return Instant.parse(cursor);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private PaymentEvent newEvent(
      String eventType, PaymentEntity payment, UUID refundId, BigDecimal amount, UUID actorUserId) {
    return new PaymentEvent(
        UUID.randomUUID(),
        eventType,
        Instant.now(),
        payment.getOrderId(),
        payment.getId(),
        refundId,
        amount,
        payment.getMethod(),
        actorUserId);
  }

  private void publishAfterCommit(PaymentEvent event) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      paymentEventPublisher.publish(event);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            paymentEventPublisher.publish(event);
          }
        });
  }

  private record PaymentSummaryTotals(
      PaymentStatus paymentStatus,
      BigDecimal paidAmount,
      BigDecimal refundedAmount,
      RefundStatus refundStatus,
      BigDecimal remainingAmount) {}
}
