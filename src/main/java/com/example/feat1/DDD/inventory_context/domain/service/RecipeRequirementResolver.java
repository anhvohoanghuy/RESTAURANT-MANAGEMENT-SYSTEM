package com.example.feat1.DDD.inventory_context.domain.service;

import com.example.feat1.DDD.inventory_context.domain.model.InventoryDomainException;
import com.example.feat1.DDD.inventory_context.domain.port.MenuRecipeCostingPort;
import com.example.feat1.DDD.inventory_context.domain.snapshot.RecipeCostingSnapshot;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.IngredientEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.IngredientRepository;
import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Shared recipe→ingredient base-quantity resolver for the Inventory Context (D-02).
 *
 * <p>Resolves a single recipe target (a DISH or a TOPPING_OPTION) into its per-ingredient required
 * base quantities: each recipe line's quantity is converted to the ingredient's base unit via
 * {@link UnitConverter} and multiplied by the order-line quantity. A missing recipe, null
 * ingredient link, unknown ingredient, or unconvertible unit contributes ZERO and never throws
 * (D-06 / T-15-06).
 *
 * <p>This is the single source of truth for recipe resolution so the whole-order reservation path
 * ({@code InventoryReservationService}) and the single-line settlement path resolve identical
 * requirements and cannot drift (D-02 / T-16-05).
 */
@Service
@RequiredArgsConstructor
public class RecipeRequirementResolver {
  private static final Logger log = LoggerFactory.getLogger(RecipeRequirementResolver.class);

  private final MenuRecipeCostingPort menuRecipeCostingPort;
  private final IngredientRepository ingredientRepository;

  /**
   * Accumulates the required base quantity per ingredient for one recipe target into {@code
   * required}, converting each recipe-line quantity to the ingredient base unit and multiplying by
   * {@code orderLineQuantity}. Contributions are merged with {@link BigDecimal#add} so repeated
   * ingredients across targets sum. Behavior-preserving extraction of the former
   * InventoryReservationService.accumulateRecipe (D-02).
   */
  public void accumulate(
      Map<UUID, BigDecimal> required,
      RecipeTargetType targetType,
      UUID targetId,
      int orderLineQuantity) {
    if (targetId == null) {
      return;
    }
    Optional<RecipeCostingSnapshot> recipe = menuRecipeCostingPort.findRecipe(targetType, targetId);
    if (recipe.isEmpty()) {
      log.debug("No recipe for {} {} — contributes zero required stock", targetType, targetId);
      return;
    }
    for (RecipeCostingSnapshot.Line line : recipe.get().lines()) {
      UUID ingredientId = line.ingredientId();
      if (ingredientId == null) {
        continue;
      }
      Optional<IngredientEntity> ingredient = ingredientRepository.findById(ingredientId);
      if (ingredient.isEmpty()) {
        log.debug("Ingredient {} not found — contributes zero required stock", ingredientId);
        continue;
      }
      String baseUnit = ingredient.get().getBaseUnit();
      BigDecimal converted;
      try {
        converted = UnitConverter.convert(line.quantity(), line.unit(), baseUnit);
      } catch (InventoryDomainException unconvertible) {
        log.debug(
            "Cannot convert {} {} to base unit {} for ingredient {} — contributes zero",
            line.quantity(),
            line.unit(),
            baseUnit,
            ingredientId);
        continue;
      }
      BigDecimal contribution = converted.multiply(BigDecimal.valueOf(orderLineQuantity));
      required.merge(ingredientId, contribution, BigDecimal::add);
    }
  }

  /**
   * Convenience for single-target callers (e.g. per-line settlement): resolves one recipe target
   * into a fresh insertion-ordered map of required base quantities per ingredient. Delegates to
   * {@link #accumulate} so the resolution logic stays in one place (D-02).
   */
  public Map<UUID, BigDecimal> resolveForTarget(
      RecipeTargetType targetType, UUID targetId, int orderLineQuantity) {
    Map<UUID, BigDecimal> required = new LinkedHashMap<>();
    accumulate(required, targetType, targetId, orderLineQuantity);
    return required;
  }
}
