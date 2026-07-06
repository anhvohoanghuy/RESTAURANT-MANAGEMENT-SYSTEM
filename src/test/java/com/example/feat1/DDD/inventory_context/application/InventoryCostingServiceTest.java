package com.example.feat1.DDD.inventory_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.IngredientCostRequest;
import com.example.feat1.DDD.inventory_context.domain.model.IngredientStatus;
import com.example.feat1.DDD.inventory_context.domain.model.InventoryDomainException;
import com.example.feat1.DDD.inventory_context.domain.port.MenuRecipeCostingPort;
import com.example.feat1.DDD.inventory_context.domain.snapshot.RecipeCostingSnapshot;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.IngredientCostEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.IngredientEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.IngredientCostRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.IngredientRepository;
import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryCostingServiceTest {
  private IngredientRepository ingredientRepository;
  private IngredientCostRepository costRepository;
  private MenuRecipeCostingPort menuRecipeCostingPort;
  private InventoryCostingService service;

  @BeforeEach
  void setUp() {
    ingredientRepository = mock(IngredientRepository.class);
    costRepository = mock(IngredientCostRepository.class);
    menuRecipeCostingPort = mock(MenuRecipeCostingPort.class);
    service =
        new InventoryCostingService(ingredientRepository, costRepository, menuRecipeCostingPort);
  }

  @Test
  void calculatesRecipeCostWithUnitConversion() {
    UUID dishId = UUID.randomUUID();
    UUID ingredientId = UUID.randomUUID();
    when(menuRecipeCostingPort.findRecipe(RecipeTargetType.DISH, dishId))
        .thenReturn(
            Optional.of(
                new RecipeCostingSnapshot(
                    RecipeTargetType.DISH,
                    dishId,
                    "Milk tea",
                    List.of(
                        new RecipeCostingSnapshot.Line(
                            UUID.randomUUID(),
                            ingredientId,
                            "Tea",
                            BigDecimal.valueOf(200),
                            "g",
                            1)))));
    when(ingredientRepository.findById(ingredientId))
        .thenReturn(Optional.of(ingredient(ingredientId)));
    when(costRepository.findTopByIngredient_IdAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
            org.mockito.ArgumentMatchers.eq(ingredientId), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Optional.of(cost(ingredient(ingredientId), BigDecimal.valueOf(100000), "kg")));

    var response = service.calculateRecipeCost(RecipeTargetType.DISH, dishId);

    assertThat(response.fullyCosted()).isTrue();
    assertThat(response.totalCost()).isEqualByComparingTo("20000.00");
    assertThat(response.lines().get(0).convertedQuantity()).isEqualByComparingTo("0.200000");
  }

  @Test
  void missingIngredientLinkIsReportedAsUncosted() {
    UUID dishId = UUID.randomUUID();
    when(menuRecipeCostingPort.findRecipe(RecipeTargetType.DISH, dishId))
        .thenReturn(
            Optional.of(
                new RecipeCostingSnapshot(
                    RecipeTargetType.DISH,
                    dishId,
                    "Milk tea",
                    List.of(
                        new RecipeCostingSnapshot.Line(
                            UUID.randomUUID(), null, "Tea", BigDecimal.valueOf(200), "g", 1)))));

    var response = service.calculateRecipeCost(RecipeTargetType.DISH, dishId);

    assertThat(response.fullyCosted()).isFalse();
    assertThat(response.totalCost()).isEqualByComparingTo("0.00");
    assertThat(response.lines().get(0).reason()).isEqualTo("INGREDIENT_NOT_LINKED");
  }

  @Test
  void archivedIngredientRejectsNewCost() {
    UUID ingredientId = UUID.randomUUID();
    IngredientEntity ingredient = ingredient(ingredientId);
    ingredient.setStatus(IngredientStatus.ARCHIVED);
    when(ingredientRepository.findById(ingredientId)).thenReturn(Optional.of(ingredient));

    assertThatThrownBy(
            () ->
                service.addCost(
                    ingredientId,
                    new IngredientCostRequest(BigDecimal.TEN, "g", Instant.now(), null, null)))
        .isInstanceOf(InventoryDomainException.class)
        .extracting("code")
        .isEqualTo(InventoryDomainException.INGREDIENT_NOT_ACTIVE);
  }

  private IngredientEntity ingredient(UUID ingredientId) {
    IngredientEntity ingredient = new IngredientEntity();
    ingredient.setId(ingredientId);
    ingredient.setName("Tea");
    ingredient.setBaseUnit("g");
    ingredient.setStatus(IngredientStatus.ACTIVE);
    ingredient.setCreatedAt(Instant.now());
    return ingredient;
  }

  private IngredientCostEntity cost(
      IngredientEntity ingredient, BigDecimal unitCost, String costUnit) {
    IngredientCostEntity cost = new IngredientCostEntity();
    cost.setId(UUID.randomUUID());
    cost.setIngredient(ingredient);
    cost.setUnitCost(unitCost);
    cost.setCostUnit(costUnit);
    cost.setEffectiveAt(Instant.now());
    cost.setCreatedAt(Instant.now());
    return cost;
  }
}
