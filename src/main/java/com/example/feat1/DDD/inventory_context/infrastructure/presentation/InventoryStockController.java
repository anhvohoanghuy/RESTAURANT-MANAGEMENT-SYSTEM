package com.example.feat1.DDD.inventory_context.infrastructure.presentation;

import com.example.feat1.DDD.auth.infrastructure.security.CustomUserDetails;
import com.example.feat1.DDD.inventory_context.application.InventoryStockService;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.StockBalanceResponse;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.StockMovementRequest;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.StockMovementResponse;
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
public class InventoryStockController {
  private final InventoryStockService inventoryStockService;

  @GetMapping("/admin/inventory/stock")
  public ResponseEntity<List<StockBalanceResponse>> listStock() {
    return ResponseEntity.ok(inventoryStockService.listStock());
  }

  @GetMapping("/admin/inventory/ingredients/{ingredientId}/stock")
  public ResponseEntity<StockBalanceResponse> ingredientStock(@PathVariable UUID ingredientId) {
    return ResponseEntity.ok(inventoryStockService.getStock(ingredientId));
  }

  @PostMapping("/admin/inventory/movements")
  public ResponseEntity<StockMovementResponse> recordMovement(
      @AuthenticationPrincipal CustomUserDetails principal,
      @RequestBody StockMovementRequest request) {
    UUID actorId = principal == null ? null : principal.getId();
    return ResponseEntity.ok(inventoryStockService.recordMovement(actorId, request));
  }

  @GetMapping("/admin/inventory/movements")
  public ResponseEntity<List<StockMovementResponse>> listMovements(
      @RequestParam(required = false) UUID ingredientId,
      @RequestParam(defaultValue = "100") int size) {
    return ResponseEntity.ok(inventoryStockService.listMovements(ingredientId, size));
  }

  @GetMapping("/admin/inventory/low-stock")
  public ResponseEntity<List<StockBalanceResponse>> listLowStock() {
    return ResponseEntity.ok(inventoryStockService.listLowStock());
  }
}
