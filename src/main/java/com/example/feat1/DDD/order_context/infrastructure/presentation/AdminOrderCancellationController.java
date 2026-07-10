package com.example.feat1.DDD.order_context.infrastructure.presentation;

import com.example.feat1.DDD.order_context.application.OrderCancellationService;
import com.example.feat1.DDD.order_context.application.dto.OrderCancellationDtos.CancelOrderLinesRequest;
import com.example.feat1.DDD.order_context.application.dto.OrderCancellationDtos.OrderCancellationResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff/ADMIN-facing order cancellation endpoints (CANCEL-02/03/04). No class/method security
 * annotation is needed here — {@code /admin/orders/**} is already {@code
 * hasAnyRole("ADMIN","STAFF")} in {@code SecurityConfig} (mirrors {@code KitchenController}'s
 * convention). The {@code userId} passed to {@link OrderCancellationService} is {@code null}, which
 * selects the no-ownership-check staff/ADMIN path (any order in window).
 */
@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderCancellationController {

  private final OrderCancellationService orderCancellationService;

  @PostMapping("/{orderId}/cancel")
  public ResponseEntity<OrderCancellationResponse> cancel(@PathVariable UUID orderId) {
    return ResponseEntity.ok(orderCancellationService.cancelOrder(null, orderId));
  }

  @PostMapping("/{orderId}/items/{lineId}/cancel")
  public ResponseEntity<OrderCancellationResponse> cancelLine(
      @PathVariable UUID orderId, @PathVariable UUID lineId) {
    return ResponseEntity.ok(
        orderCancellationService.cancelOrderLines(
            null, orderId, new CancelOrderLinesRequest(List.of(lineId))));
  }
}
