package com.example.feat1.DDD.order_context.infrastructure.presentation;

import com.example.feat1.DDD.auth.infrastructure.security.CustomUserDetails;
import com.example.feat1.DDD.order_context.application.OrderCancellationService;
import com.example.feat1.DDD.order_context.application.OrderSubmissionService;
import com.example.feat1.DDD.order_context.application.dto.OrderCancellationDtos.CancelOrderLinesRequest;
import com.example.feat1.DDD.order_context.application.dto.OrderCancellationDtos.OrderCancellationResponse;
import com.example.feat1.DDD.order_context.application.dto.OrderDtos.SubmittedOrderResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
  private final OrderSubmissionService orderSubmissionService;
  private final OrderCancellationService orderCancellationService;

  @PostMapping
  public ResponseEntity<SubmittedOrderResponse> submit(
      @AuthenticationPrincipal CustomUserDetails principal) {
    return ResponseEntity.ok(orderSubmissionService.submit(principal.getId()));
  }

  @GetMapping
  public ResponseEntity<List<SubmittedOrderResponse>> list(
      @AuthenticationPrincipal CustomUserDetails principal) {
    return ResponseEntity.ok(orderSubmissionService.listOrders(principal.getId()));
  }

  @GetMapping("/{orderId}")
  public ResponseEntity<SubmittedOrderResponse> get(
      @AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID orderId) {
    return ResponseEntity.ok(orderSubmissionService.getOrder(principal.getId(), orderId));
  }

  /**
   * Whole-order cancel of the caller's own order (CANCEL-03). Ownership is enforced by the service
   * ({@code findByIdAndUserId}) — a non-owner receives 404, never a 403 (IDOR-safe).
   */
  @PostMapping("/{orderId}/cancel")
  public ResponseEntity<OrderCancellationResponse> cancel(
      @AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID orderId) {
    return ResponseEntity.ok(orderCancellationService.cancelOrder(principal.getId(), orderId));
  }

  /**
   * Partial (single-line) cancel of the caller's own order (CANCEL-04). Same IDOR-safe ownership
   * check as the whole-order path.
   */
  @PostMapping("/{orderId}/items/{lineId}/cancel")
  public ResponseEntity<OrderCancellationResponse> cancelLine(
      @AuthenticationPrincipal CustomUserDetails principal,
      @PathVariable UUID orderId,
      @PathVariable UUID lineId) {
    return ResponseEntity.ok(
        orderCancellationService.cancelOrderLines(
            principal.getId(), orderId, new CancelOrderLinesRequest(List.of(lineId))));
  }
}
