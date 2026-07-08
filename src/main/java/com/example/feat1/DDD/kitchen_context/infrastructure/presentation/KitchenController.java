package com.example.feat1.DDD.kitchen_context.infrastructure.presentation;

import com.example.feat1.DDD.auth.infrastructure.security.CustomUserDetails;
import com.example.feat1.DDD.kitchen_context.application.KitchenBoardService;
import com.example.feat1.DDD.kitchen_context.application.KitchenTicketAdvanceService;
import com.example.feat1.DDD.kitchen_context.application.dto.AdvanceItemStatusRequest;
import com.example.feat1.DDD.kitchen_context.application.dto.KitchenBoardItemResponse;
import com.example.feat1.DDD.kitchen_context.application.dto.KitchenItemResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff-facing kitchen endpoints. No class/method security annotation is needed here — {@code
 * /admin/orders/**} is already {@code hasAnyRole("ADMIN","STAFF")} in {@code SecurityConfig}.
 */
@RestController
@RequiredArgsConstructor
public class KitchenController {

  private final KitchenTicketAdvanceService kitchenTicketAdvanceService;
  private final KitchenBoardService kitchenBoardService;

  @PatchMapping("/admin/orders/{orderId}/items/{itemId}/status")
  public ResponseEntity<KitchenItemResponse> advanceItemStatus(
      @AuthenticationPrincipal CustomUserDetails principal,
      @PathVariable UUID orderId,
      @PathVariable UUID itemId,
      @RequestBody AdvanceItemStatusRequest request) {
    return ResponseEntity.ok(
        kitchenTicketAdvanceService.advance(orderId, itemId, request, principal.getId()));
  }

  @GetMapping("/admin/orders/kitchen-board")
  public ResponseEntity<List<KitchenBoardItemResponse>> kitchenBoard() {
    return ResponseEntity.ok(kitchenBoardService.board());
  }
}
