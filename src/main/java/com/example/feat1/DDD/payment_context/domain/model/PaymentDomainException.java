package com.example.feat1.DDD.payment_context.domain.model;

import com.example.feat1.common.exception.AppException;
import org.springframework.http.HttpStatus;

public class PaymentDomainException extends AppException {
  public static final String ORDER_NOT_FOUND = "PAYMENT_ORDER_NOT_FOUND";
  public static final String PAYMENT_NOT_FOUND = "PAYMENT_NOT_FOUND";
  public static final String AMOUNT_INVALID = "PAYMENT_AMOUNT_INVALID";
  public static final String OVERPAY_NOT_ALLOWED = "PAYMENT_OVERPAY_NOT_ALLOWED";
  public static final String REFUND_AMOUNT_INVALID = "PAYMENT_REFUND_AMOUNT_INVALID";
  public static final String REFUND_EXCEEDS_PAYMENT = "PAYMENT_REFUND_EXCEEDS_PAYMENT";
  public static final String IDEMPOTENCY_KEY_REQUIRED = "PAYMENT_IDEMPOTENCY_KEY_REQUIRED";
  public static final String ORDER_NOT_OWNED = "PAYMENT_ORDER_NOT_OWNED";

  private PaymentDomainException(String code, String message, HttpStatus status) {
    super(code, message, status);
  }

  public static PaymentDomainException orderNotFound() {
    return new PaymentDomainException(ORDER_NOT_FOUND, "Order was not found", HttpStatus.NOT_FOUND);
  }

  public static PaymentDomainException paymentNotFound() {
    return new PaymentDomainException(
        PAYMENT_NOT_FOUND, "Payment was not found", HttpStatus.NOT_FOUND);
  }

  public static PaymentDomainException amountInvalid() {
    return new PaymentDomainException(
        AMOUNT_INVALID, "Payment amount must be positive", HttpStatus.BAD_REQUEST);
  }

  public static PaymentDomainException overpayNotAllowed() {
    return new PaymentDomainException(
        OVERPAY_NOT_ALLOWED,
        "Payment amount exceeds the remaining order balance",
        HttpStatus.BAD_REQUEST);
  }

  public static PaymentDomainException refundAmountInvalid() {
    return new PaymentDomainException(
        REFUND_AMOUNT_INVALID, "Refund amount must be positive", HttpStatus.BAD_REQUEST);
  }

  public static PaymentDomainException refundExceedsPayment() {
    return new PaymentDomainException(
        REFUND_EXCEEDS_PAYMENT, "Refund amount exceeds the payment amount", HttpStatus.BAD_REQUEST);
  }

  public static PaymentDomainException idempotencyKeyRequired() {
    return new PaymentDomainException(
        IDEMPOTENCY_KEY_REQUIRED, "Idempotency key is required", HttpStatus.BAD_REQUEST);
  }

  public static PaymentDomainException orderNotOwned() {
    return new PaymentDomainException(
        ORDER_NOT_OWNED, "Order does not belong to the current user", HttpStatus.FORBIDDEN);
  }
}
