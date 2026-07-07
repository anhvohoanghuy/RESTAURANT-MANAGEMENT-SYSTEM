package com.example.feat1.DDD.inventory_context.application;

import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.StockBalanceResponse;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.StockMovementRequest;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.StockMovementResponse;
import com.example.feat1.DDD.inventory_context.domain.model.IngredientStatus;
import com.example.feat1.DDD.inventory_context.domain.model.InventoryDomainException;
import com.example.feat1.DDD.inventory_context.domain.model.InventoryMovementType;
import com.example.feat1.DDD.inventory_context.domain.service.UnitConverter;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.IngredientEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryStockBalanceEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryStockMovementEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.IngredientRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryStockBalanceRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryStockMovementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operational stock management for the Inventory Context: records immutable movements and keeps the
 * single-location stock balance consistent, and exposes stock reads for staff/admin. Costing, menu,
 * order, and payment behavior are intentionally untouched.
 */
@Service
@RequiredArgsConstructor
public class InventoryStockService {
  private static final int QUANTITY_SCALE = 6;
  private static final int DEFAULT_MOVEMENT_LIMIT = 100;
  private static final int MAX_MOVEMENT_LIMIT = 500;
  private static final String DEFAULT_LOCATION = InventoryStockBalanceEntity.DEFAULT_LOCATION;

  private final IngredientRepository ingredientRepository;
  private final InventoryStockBalanceRepository balanceRepository;
  private final InventoryStockMovementRepository movementRepository;

  @Transactional
  public StockMovementResponse recordMovement(UUID actorId, StockMovementRequest request) {
    if (request == null || request.ingredientId() == null || request.type() == null) {
      throw InventoryDomainException.movementInvalid("Ingredient and movement type are required");
    }
    IngredientEntity ingredient = requireIngredient(request.ingredientId());
    if (ingredient.getStatus() != IngredientStatus.ACTIVE) {
      throw InventoryDomainException.ingredientNotActive();
    }
    BigDecimal quantity = request.quantity();
    if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
      throw InventoryDomainException.movementQuantityInvalid();
    }
    BigDecimal threshold = normalizeThreshold(request.lowStockThreshold());

    String baseUnit = ingredient.getBaseUnit();
    // Throws INVENTORY_UNIT_CONVERSION_UNSUPPORTED when the entered unit cannot be converted.
    BigDecimal baseQuantity = UnitConverter.convert(quantity, request.unit(), baseUnit);

    InventoryStockBalanceEntity balance =
        balanceRepository
            .findByIngredient_IdAndLocationCode(ingredient.getId(), DEFAULT_LOCATION)
            .orElseGet(() -> newBalance(ingredient));

    BigDecimal current = balance.getQuantityOnHand();
    InventoryMovementType type = request.type();
    BigDecimal delta;
    BigDecimal resulting;
    if (type.isInbound()) {
      delta = baseQuantity;
      resulting = current.add(delta);
    } else if (type.isOutbound()) {
      if (current.compareTo(baseQuantity) < 0) {
        throw InventoryDomainException.stockInsufficient();
      }
      delta = baseQuantity.negate();
      resulting = current.add(delta);
    } else {
      // STOCK_COUNT: explicit correction path that sets on-hand to the counted quantity.
      resulting = baseQuantity;
      delta = resulting.subtract(current);
    }
    delta = scale(delta);
    resulting = scale(resulting);

    Instant now = Instant.now();
    balance.setQuantityOnHand(resulting);
    balance.setBaseUnit(baseUnit);
    if (threshold != null) {
      balance.setLowStockThreshold(threshold);
    }
    balance.setLastMovementAt(now);
    balance.setUpdatedAt(now);
    balanceRepository.save(balance);

    InventoryStockMovementEntity movement = new InventoryStockMovementEntity();
    movement.setIngredient(ingredient);
    movement.setLocationCode(DEFAULT_LOCATION);
    movement.setMovementType(type);
    movement.setQuantity(scale(quantity));
    movement.setUnit(UnitConverter.normalizeUnit(request.unit()));
    movement.setBaseQuantityDelta(delta);
    movement.setBaseUnit(baseUnit);
    movement.setResultingBalance(resulting);
    movement.setNote(blankToNull(request.note()));
    movement.setReferenceType(blankToNull(request.referenceType()));
    movement.setReferenceId(request.referenceId());
    movement.setActorId(actorId);
    movement.setCreatedAt(now);
    movementRepository.save(movement);

    return toMovementResponse(movement);
  }

  @Transactional(readOnly = true)
  public List<StockBalanceResponse> listStock() {
    return balanceRepository.findAllByOrderByIngredient_NameAsc().stream()
        .map(this::toBalanceResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public StockBalanceResponse getStock(UUID ingredientId) {
    requireIngredient(ingredientId);
    InventoryStockBalanceEntity balance =
        balanceRepository
            .findByIngredient_IdAndLocationCode(ingredientId, DEFAULT_LOCATION)
            .orElseThrow(InventoryDomainException::stockNotFound);
    return toBalanceResponse(balance);
  }

  @Transactional(readOnly = true)
  public List<StockBalanceResponse> listLowStock() {
    return balanceRepository.findLowStock().stream().map(this::toBalanceResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<StockMovementResponse> listMovements(UUID ingredientId, int size) {
    Pageable pageable = PageRequest.of(0, clampLimit(size));
    List<InventoryStockMovementEntity> movements =
        ingredientId == null
            ? movementRepository.findAllByOrderByCreatedAtDesc(pageable)
            : movementRepository.findByIngredient_IdOrderByCreatedAtDesc(ingredientId, pageable);
    return movements.stream().map(this::toMovementResponse).toList();
  }

  private InventoryStockBalanceEntity newBalance(IngredientEntity ingredient) {
    InventoryStockBalanceEntity balance = new InventoryStockBalanceEntity();
    balance.setIngredient(ingredient);
    balance.setLocationCode(DEFAULT_LOCATION);
    balance.setQuantityOnHand(BigDecimal.ZERO);
    balance.setBaseUnit(ingredient.getBaseUnit());
    balance.setCreatedAt(Instant.now());
    return balance;
  }

  private IngredientEntity requireIngredient(UUID ingredientId) {
    return ingredientRepository
        .findById(ingredientId)
        .orElseThrow(InventoryDomainException::ingredientNotFound);
  }

  private StockBalanceResponse toBalanceResponse(InventoryStockBalanceEntity balance) {
    IngredientEntity ingredient = balance.getIngredient();
    BigDecimal threshold = balance.getLowStockThreshold();
    boolean lowStock = threshold != null && balance.getQuantityOnHand().compareTo(threshold) <= 0;
    return new StockBalanceResponse(
        ingredient.getId(),
        ingredient.getName(),
        ingredient.getStatus(),
        balance.getQuantityOnHand(),
        balance.getBaseUnit(),
        threshold,
        lowStock,
        balance.getLastMovementAt());
  }

  private StockMovementResponse toMovementResponse(InventoryStockMovementEntity movement) {
    IngredientEntity ingredient = movement.getIngredient();
    return new StockMovementResponse(
        movement.getId(),
        ingredient.getId(),
        ingredient.getName(),
        movement.getMovementType(),
        movement.getQuantity(),
        movement.getUnit(),
        movement.getBaseQuantityDelta(),
        movement.getBaseUnit(),
        movement.getResultingBalance(),
        movement.getNote(),
        movement.getReferenceType(),
        movement.getReferenceId(),
        movement.getActorId(),
        movement.getCreatedAt());
  }

  private BigDecimal normalizeThreshold(BigDecimal threshold) {
    if (threshold == null) {
      return null;
    }
    if (threshold.compareTo(BigDecimal.ZERO) < 0) {
      throw InventoryDomainException.movementInvalid("Low-stock threshold cannot be negative");
    }
    return scale(threshold);
  }

  private BigDecimal scale(BigDecimal value) {
    return value.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
  }

  private int clampLimit(int size) {
    if (size <= 0) {
      return DEFAULT_MOVEMENT_LIMIT;
    }
    return Math.min(size, MAX_MOVEMENT_LIMIT);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
