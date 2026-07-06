package com.example.feat1.DDD.inventory_context.infrastructure.presentation;

import com.example.feat1.DDD.inventory_context.application.InventoryCostingService;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.IngredientCostRequest;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.IngredientCostResponse;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.IngredientRequest;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.IngredientResponse;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.MenuCostingResponse;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.RecipeCostResponse;
import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InventoryController {
  private final InventoryCostingService inventoryCostingService;

  @PostMapping("/admin/inventory/ingredients")
  public ResponseEntity<IngredientResponse> createIngredient(
      @RequestBody IngredientRequest request) {
    return ResponseEntity.ok(inventoryCostingService.createIngredient(request));
  }

  @PutMapping("/admin/inventory/ingredients/{ingredientId}")
  public ResponseEntity<IngredientResponse> updateIngredient(
      @PathVariable UUID ingredientId, @RequestBody IngredientRequest request) {
    return ResponseEntity.ok(inventoryCostingService.updateIngredient(ingredientId, request));
  }

  @DeleteMapping("/admin/inventory/ingredients/{ingredientId}")
  public ResponseEntity<IngredientResponse> archiveIngredient(@PathVariable UUID ingredientId) {
    return ResponseEntity.ok(inventoryCostingService.archiveIngredient(ingredientId));
  }

  @GetMapping("/admin/inventory/ingredients")
  public ResponseEntity<List<IngredientResponse>> listIngredients(
      @RequestParam(required = false) String search) {
    return ResponseEntity.ok(inventoryCostingService.listIngredients(search));
  }

  @PostMapping("/admin/inventory/ingredients/{ingredientId}/costs")
  public ResponseEntity<IngredientCostResponse> addCost(
      @PathVariable UUID ingredientId, @RequestBody IngredientCostRequest request) {
    return ResponseEntity.ok(inventoryCostingService.addCost(ingredientId, request));
  }

  @GetMapping("/admin/inventory/ingredients/{ingredientId}/costs")
  public ResponseEntity<List<IngredientCostResponse>> listCosts(@PathVariable UUID ingredientId) {
    return ResponseEntity.ok(inventoryCostingService.listCosts(ingredientId));
  }

  @GetMapping("/admin/menu/recipes/cost")
  public ResponseEntity<RecipeCostResponse> recipeCost(
      @RequestParam RecipeTargetType targetType, @RequestParam UUID targetId) {
    return ResponseEntity.ok(inventoryCostingService.calculateRecipeCost(targetType, targetId));
  }

  @GetMapping("/admin/menu/costing")
  public ResponseEntity<MenuCostingResponse> menuCosting() {
    return ResponseEntity.ok(inventoryCostingService.listMenuCosting());
  }
}
