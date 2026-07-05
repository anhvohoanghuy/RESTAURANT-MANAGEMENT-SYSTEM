package com.example.feat1.DDD.order_context.infrastructure.presentation;

import com.example.feat1.DDD.auth.infrastructure.security.CustomUserDetails;
import com.example.feat1.DDD.order_context.application.OrderSubmissionService;
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
}
