package com.example.feat1.DDD.payment_context.infrastructure.presentation;

import com.example.feat1.DDD.auth.infrastructure.security.CustomUserDetails;
import com.example.feat1.DDD.payment_context.application.PaymentService;
import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.CreateQrPaymentRequest;
import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.PaymentHistoryResponse;
import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.PaymentResponse;
import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.QrPaymentRequestResponse;
import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.RecordPaymentRequest;
import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.RecordRefundRequest;
import com.example.feat1.DDD.payment_context.application.dto.PaymentDtos.RefundResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentController {
  private final PaymentService paymentService;

  @GetMapping("/orders/{orderId}/payments")
  public ResponseEntity<List<PaymentResponse>> getOrderPayments(
      @AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID orderId) {
    return ResponseEntity.ok(paymentService.getOrderPayments(principal.getId(), orderId));
  }

  @PostMapping("/orders/{orderId}/payment-requests/qr")
  public ResponseEntity<QrPaymentRequestResponse> createQrPaymentRequest(
      @AuthenticationPrincipal CustomUserDetails principal,
      @PathVariable UUID orderId,
      @RequestBody CreateQrPaymentRequest request) {
    return ResponseEntity.ok(
        paymentService.createQrPaymentRequest(principal.getId(), orderId, request));
  }

  @PostMapping("/admin/orders/{orderId}/payments")
  public ResponseEntity<PaymentResponse> recordPayment(
      @AuthenticationPrincipal CustomUserDetails principal,
      @PathVariable UUID orderId,
      @RequestBody RecordPaymentRequest request) {
    return ResponseEntity.ok(paymentService.recordPayment(principal.getId(), orderId, request));
  }

  @PostMapping("/admin/payments/{paymentId}/refunds")
  public ResponseEntity<RefundResponse> recordRefund(
      @AuthenticationPrincipal CustomUserDetails principal,
      @PathVariable UUID paymentId,
      @RequestBody RecordRefundRequest request) {
    return ResponseEntity.ok(paymentService.recordRefund(principal.getId(), paymentId, request));
  }

  @GetMapping("/admin/payments")
  public ResponseEntity<PaymentHistoryResponse> listPayments(
      @RequestParam(required = false) UUID orderId,
      @RequestParam(required = false) UUID orderUserId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") int size) {
    return ResponseEntity.ok(paymentService.listPayments(orderId, orderUserId, cursor, size));
  }
}
