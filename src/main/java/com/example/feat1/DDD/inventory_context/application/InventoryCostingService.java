package com.example.feat1.DDD.inventory_context.application;

import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.IngredientCostRequest;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.IngredientCostResponse;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.IngredientRequest;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.IngredientResponse;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.MenuCostingItemResponse;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.MenuCostingResponse;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.RecipeCostLineResponse;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.RecipeCostResponse;
import com.example.feat1.DDD.inventory_context.domain.model.IngredientStatus;
import com.example.feat1.DDD.inventory_context.domain.model.InventoryDomainException;
import com.example.feat1.DDD.inventory_context.domain.port.MenuRecipeCostingPort;
import com.example.feat1.DDD.inventory_context.domain.service.UnitConverter;
import com.example.feat1.DDD.inventory_context.domain.snapshot.MenuCostingDishSnapshot;
import com.example.feat1.DDD.inventory_context.domain.snapshot.RecipeCostingSnapshot;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.IngredientCostEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.IngredientEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.IngredientCostRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.IngredientRepository;
import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryCostingService {
  private static final int MONEY_SCALE = 2;

  private final IngredientRepository ingredientRepository;
  private final IngredientCostRepository costRepository;
  private final MenuRecipeCostingPort menuRecipeCostingPort;

  @Transactional
  public IngredientResponse createIngredient(IngredientRequest request) {
    IngredientEntity ingredient = new IngredientEntity();
    ingredient.setName(requiredText(request.name(), "Ingredient name is required"));
    ingredient.setBaseUnit(normalizeUnit(request.baseUnit()));
    ingredient.setDescription(blankToNull(request.description()));
    ingredient.setStatus(request.status() == null ? IngredientStatus.ACTIVE : request.status());
    ingredient.setCreatedAt(Instant.now());
    return toIngredientResponse(ingredientRepository.save(ingredient));
  }

  @Transactional
  public IngredientResponse updateIngredient(UUID ingredientId, IngredientRequest request) {
    IngredientEntity ingredient = requireIngredient(ingredientId);
    ingredient.setName(requiredText(request.name(), "Ingredient name is required"));
    ingredient.setBaseUnit(normalizeUnit(request.baseUnit()));
    ingredient.setDescription(blankToNull(request.description()));
    ingredient.setStatus(request.status() == null ? IngredientStatus.ACTIVE : request.status());
    ingredient.setUpdatedAt(Instant.now());
    return toIngredientResponse(ingredientRepository.save(ingredient));
  }

  @Transactional
  public IngredientResponse archiveIngredient(UUID ingredientId) {
    IngredientEntity ingredient = requireIngredient(ingredientId);
    ingredient.setStatus(IngredientStatus.ARCHIVED);
    ingredient.setUpdatedAt(Instant.now());
    return toIngredientResponse(ingredientRepository.save(ingredient));
  }

  @Transactional(readOnly = true)
  public List<IngredientResponse> listIngredients(String search) {
    List<IngredientEntity> ingredients =
        search == null || search.isBlank()
            ? ingredientRepository.findAllByOrderByNameAsc()
            : ingredientRepository.findByNameContainingIgnoreCaseOrderByNameAsc(search.trim());
    return ingredients.stream().map(this::toIngredientResponse).toList();
  }

  @Transactional
  public IngredientCostResponse addCost(UUID ingredientId, IngredientCostRequest request) {
    IngredientEntity ingredient = requireIngredient(ingredientId);
    if (ingredient.getStatus() != IngredientStatus.ACTIVE) {
      throw InventoryDomainException.ingredientNotActive();
    }
    validateCost(request.unitCost());

    IngredientCostEntity cost = new IngredientCostEntity();
    cost.setIngredient(ingredient);
    cost.setUnitCost(request.unitCost());
    cost.setCostUnit(normalizeUnit(request.costUnit()));
    cost.setEffectiveAt(request.effectiveAt() == null ? Instant.now() : request.effectiveAt());
    cost.setSource(blankToNull(request.source()));
    cost.setNote(blankToNull(request.note()));
    cost.setCreatedAt(Instant.now());
    return toCostResponse(costRepository.save(cost));
  }

  @Transactional(readOnly = true)
  public List<IngredientCostResponse> listCosts(UUID ingredientId) {
    requireIngredient(ingredientId);
    return costRepository.findByIngredient_IdOrderByEffectiveAtDesc(ingredientId).stream()
        .map(this::toCostResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public RecipeCostResponse calculateRecipeCost(RecipeTargetType targetType, UUID targetId) {
    RecipeCostingSnapshot recipe =
        menuRecipeCostingPort
            .findRecipe(targetType, targetId)
            .orElseThrow(() -> new EntityNotFoundException("Recipe not found"));
    return calculateRecipe(recipe);
  }

  @Transactional(readOnly = true)
  public MenuCostingResponse listMenuCosting() {
    List<MenuCostingItemResponse> items =
        menuRecipeCostingPort.listActiveDishes().stream().map(this::toMenuCostingItem).toList();
    return new MenuCostingResponse(items);
  }

  private MenuCostingItemResponse toMenuCostingItem(MenuCostingDishSnapshot dish) {
    Optional<RecipeCostingSnapshot> recipe =
        menuRecipeCostingPort.findRecipe(RecipeTargetType.DISH, dish.dishId());
    RecipeCostResponse cost =
        recipe
            .map(this::calculateRecipe)
            .orElse(
                new RecipeCostResponse(
                    RecipeTargetType.DISH, dish.dishId(), null, BigDecimal.ZERO, false, List.of()));
    int uncosted =
        (int) cost.lines().stream().filter(line -> !line.costed()).count()
            + (recipe.isEmpty() ? 1 : 0);
    BigDecimal margin = dish.sellPrice().subtract(cost.totalCost());
    return new MenuCostingItemResponse(
        dish.dishId(),
        dish.dishName(),
        dish.sellPrice(),
        cost.totalCost(),
        margin,
        marginPercent(margin, dish.sellPrice()),
        cost.fullyCosted() && recipe.isPresent(),
        uncosted);
  }

  private RecipeCostResponse calculateRecipe(RecipeCostingSnapshot recipe) {
    List<RecipeCostLineResponse> lines = recipe.lines().stream().map(this::calculateLine).toList();
    BigDecimal total =
        lines.stream()
            .map(RecipeCostLineResponse::lineCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    boolean fullyCosted = lines.stream().allMatch(RecipeCostLineResponse::costed);
    return new RecipeCostResponse(
        recipe.targetType(), recipe.targetId(), recipe.recipeName(), total, fullyCosted, lines);
  }

  private RecipeCostLineResponse calculateLine(RecipeCostingSnapshot.Line line) {
    if (line.ingredientId() == null) {
      return uncosted(line, "INGREDIENT_NOT_LINKED");
    }
    Optional<IngredientEntity> ingredient = ingredientRepository.findById(line.ingredientId());
    if (ingredient.isEmpty()) {
      return uncosted(line, "INGREDIENT_NOT_FOUND");
    }
    if (ingredient.get().getStatus() != IngredientStatus.ACTIVE) {
      return uncosted(line, "INGREDIENT_INACTIVE");
    }
    Optional<IngredientCostEntity> cost =
        costRepository.findTopByIngredient_IdAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
            line.ingredientId(), Instant.now());
    if (cost.isEmpty()) {
      return uncosted(line, "COST_NOT_FOUND");
    }
    BigDecimal converted;
    try {
      converted = UnitConverter.convert(line.quantity(), line.unit(), cost.get().getCostUnit());
    } catch (InventoryDomainException exception) {
      return uncosted(line, "UNIT_CONVERSION_UNSUPPORTED");
    }
    BigDecimal lineCost =
        converted.multiply(cost.get().getUnitCost()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    return new RecipeCostLineResponse(
        line.recipeLineId(),
        line.ingredientId(),
        line.ingredientName(),
        line.quantity(),
        line.unit(),
        converted,
        cost.get().getCostUnit(),
        cost.get().getUnitCost(),
        lineCost,
        true,
        null);
  }

  private RecipeCostLineResponse uncosted(RecipeCostingSnapshot.Line line, String reason) {
    return new RecipeCostLineResponse(
        line.recipeLineId(),
        line.ingredientId(),
        line.ingredientName(),
        line.quantity(),
        line.unit(),
        null,
        null,
        null,
        BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
        false,
        reason);
  }

  private IngredientEntity requireIngredient(UUID ingredientId) {
    return ingredientRepository
        .findById(ingredientId)
        .orElseThrow(InventoryDomainException::ingredientNotFound);
  }

  private IngredientResponse toIngredientResponse(IngredientEntity ingredient) {
    return new IngredientResponse(
        ingredient.getId(),
        ingredient.getName(),
        ingredient.getBaseUnit(),
        ingredient.getDescription(),
        ingredient.getStatus(),
        ingredient.getCreatedAt(),
        ingredient.getUpdatedAt());
  }

  private IngredientCostResponse toCostResponse(IngredientCostEntity cost) {
    return new IngredientCostResponse(
        cost.getId(),
        cost.getIngredient().getId(),
        cost.getUnitCost(),
        cost.getCostUnit(),
        cost.getEffectiveAt(),
        cost.getSource(),
        cost.getNote(),
        cost.getCreatedAt());
  }

  private BigDecimal marginPercent(BigDecimal margin, BigDecimal sellPrice) {
    if (sellPrice == null || sellPrice.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    return margin.multiply(BigDecimal.valueOf(100)).divide(sellPrice, 2, RoundingMode.HALF_UP);
  }

  private void validateCost(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw InventoryDomainException.costInvalid();
    }
  }

  private String normalizeUnit(String unit) {
    return UnitConverter.normalizeUnit(unit);
  }

  private String requiredText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
