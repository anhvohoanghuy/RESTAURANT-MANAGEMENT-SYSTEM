package com.example.feat1.DDD.order_context.infrastructure.presentation;

import com.example.feat1.DDD.auth.infrastructure.security.CustomUserDetails;
import com.example.feat1.DDD.order_context.application.CartService;
import com.example.feat1.DDD.order_context.application.dto.CartDtos.AddCartItemRequest;
import com.example.feat1.DDD.order_context.application.dto.CartDtos.CartResponse;
import com.example.feat1.DDD.order_context.application.dto.CartDtos.UpdateCartLineQuantityRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {
  private final CartService cartService;

  @GetMapping
  public ResponseEntity<CartResponse> getCart(
      @AuthenticationPrincipal CustomUserDetails principal) {
    return ResponseEntity.ok(cartService.getCart(principal.getId()));
  }

  @PostMapping("/items")
  public ResponseEntity<CartResponse> addItem(
      @AuthenticationPrincipal CustomUserDetails principal,
      @RequestBody AddCartItemRequest request) {
    return ResponseEntity.ok(cartService.addItem(principal.getId(), request));
  }

  @PatchMapping("/items/{lineId}")
  public ResponseEntity<CartResponse> updateLineQuantity(
      @AuthenticationPrincipal CustomUserDetails principal,
      @PathVariable UUID lineId,
      @RequestBody UpdateCartLineQuantityRequest request) {
    return ResponseEntity.ok(cartService.updateLineQuantity(principal.getId(), lineId, request));
  }

  @DeleteMapping("/items/{lineId}")
  public ResponseEntity<CartResponse> removeLine(
      @AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID lineId) {
    return ResponseEntity.ok(cartService.removeLine(principal.getId(), lineId));
  }

  @DeleteMapping
  public ResponseEntity<CartResponse> clearCart(
      @AuthenticationPrincipal CustomUserDetails principal) {
    return ResponseEntity.ok(cartService.clearCart(principal.getId()));
  }
}
