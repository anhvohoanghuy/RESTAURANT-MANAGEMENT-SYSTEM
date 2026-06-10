package com.example.feat1.DDD.menu_context.infrastructure.presentation;

import com.example.feat1.DDD.menu_context.application.MenuCatalogService;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.CategoryRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.CategoryResponse;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.DishRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.DishResponse;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.RecipeRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.RecipeResponse;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.ToppingGroupRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.ToppingGroupResponse;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.ToppingOptionRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.ToppingOptionResponse;
import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/menu")
@RequiredArgsConstructor
public class AdminMenuController {
  private final MenuCatalogService menuCatalogService;

  @PostMapping("/categories")
  public ResponseEntity<CategoryResponse> createCategory(@RequestBody CategoryRequest request) {
    return ResponseEntity.ok(menuCatalogService.createCategory(request));
  }

  @PutMapping("/categories/{id}")
  public ResponseEntity<CategoryResponse> updateCategory(
      @PathVariable UUID id, @RequestBody CategoryRequest request) {
    return ResponseEntity.ok(menuCatalogService.updateCategory(id, request));
  }

  @DeleteMapping("/categories/{id}")
  public ResponseEntity<CategoryResponse> archiveCategory(@PathVariable UUID id) {
    return ResponseEntity.ok(menuCatalogService.archiveCategory(id));
  }

  @PostMapping("/dishes")
  public ResponseEntity<DishResponse> createDish(@RequestBody DishRequest request) {
    return ResponseEntity.ok(menuCatalogService.createDish(request));
  }

  @PutMapping("/dishes/{id}")
  public ResponseEntity<DishResponse> updateDish(
      @PathVariable UUID id, @RequestBody DishRequest request) {
    return ResponseEntity.ok(menuCatalogService.updateDish(id, request));
  }

  @DeleteMapping("/dishes/{id}")
  public ResponseEntity<DishResponse> archiveDish(@PathVariable UUID id) {
    return ResponseEntity.ok(menuCatalogService.archiveDish(id));
  }

  @PostMapping("/topping-groups")
  public ResponseEntity<ToppingGroupResponse> createToppingGroup(
      @RequestBody ToppingGroupRequest request) {
    return ResponseEntity.ok(menuCatalogService.createToppingGroup(request));
  }

  @PostMapping("/topping-options")
  public ResponseEntity<ToppingOptionResponse> createToppingOption(
      @RequestBody ToppingOptionRequest request) {
    return ResponseEntity.ok(menuCatalogService.createToppingOption(request));
  }

  @DeleteMapping("/topping-options/{id}")
  public ResponseEntity<ToppingOptionResponse> archiveToppingOption(@PathVariable UUID id) {
    return ResponseEntity.ok(menuCatalogService.archiveToppingOption(id));
  }

  @PutMapping("/recipes")
  public ResponseEntity<RecipeResponse> upsertRecipe(@RequestBody RecipeRequest request) {
    return ResponseEntity.ok(menuCatalogService.upsertRecipe(request));
  }

  @GetMapping("/recipes")
  public ResponseEntity<RecipeResponse> getRecipe(
      @RequestParam RecipeTargetType targetType, @RequestParam UUID targetId) {
    return ResponseEntity.ok(menuCatalogService.getRecipe(targetType, targetId));
  }
}
