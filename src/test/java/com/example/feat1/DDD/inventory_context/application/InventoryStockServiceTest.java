package com.example.feat1.DDD.inventory_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.StockBalanceResponse;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.StockMovementRequest;
import com.example.feat1.DDD.inventory_context.application.dto.InventoryDtos.StockMovementResponse;
import com.example.feat1.DDD.inventory_context.domain.model.IngredientStatus;
import com.example.feat1.DDD.inventory_context.domain.model.InventoryDomainException;
import com.example.feat1.DDD.inventory_context.domain.model.InventoryMovementType;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.IngredientEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryStockBalanceEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryStockMovementEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.IngredientRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryStockBalanceRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryStockMovementRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryStockServiceTest {
  private IngredientRepository ingredientRepository;
  private InventoryStockBalanceRepository balanceRepository;
  private InventoryStockMovementRepository movementRepository;
  private InventoryStockService service;

  @BeforeEach
  void setUp() {
    ingredientRepository = mock(IngredientRepository.class);
    balanceRepository = mock(InventoryStockBalanceRepository.class);
    movementRepository = mock(InventoryStockMovementRepository.class);
    service =
        new InventoryStockService(ingredientRepository, balanceRepository, movementRepository);
    when(balanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void receiptConvertsUnitAndIncreasesBalance() {
    UUID ingredientId = UUID.randomUUID();
    IngredientEntity ingredient = ingredient(ingredientId, "g");
    when(ingredientRepository.findById(ingredientId)).thenReturn(Optional.of(ingredient));
    when(balanceRepository.findByIngredient_IdAndLocationCode(ingredientId, "DEFAULT"))
        .thenReturn(Optional.empty());

    StockMovementResponse response =
        service.recordMovement(
            UUID.randomUUID(),
            new StockMovementRequest(
                ingredientId,
                InventoryMovementType.RECEIPT,
                BigDecimal.valueOf(2),
                "kg",
                "delivery",
                null,
                null,
                null));

    assertThat(response.type()).isEqualTo(InventoryMovementType.RECEIPT);
    assertThat(response.baseUnit()).isEqualTo("g");
    assertThat(response.baseQuantityDelta()).isEqualByComparingTo("2000.000000");
    assertThat(response.resultingBalance()).isEqualByComparingTo("2000.000000");
    verify(movementRepository).save(any(InventoryStockMovementEntity.class));
  }

  @Test
  void wasteWithInsufficientStockIsRejectedAndBalanceUntouched() {
    UUID ingredientId = UUID.randomUUID();
    IngredientEntity ingredient = ingredient(ingredientId, "g");
    when(ingredientRepository.findById(ingredientId)).thenReturn(Optional.of(ingredient));
    InventoryStockBalanceEntity balance = balance(ingredient, BigDecimal.valueOf(1000), null);
    when(balanceRepository.findByIngredient_IdAndLocationCode(ingredientId, "DEFAULT"))
        .thenReturn(Optional.of(balance));

    assertThatThrownBy(
            () ->
                service.recordMovement(
                    null,
                    new StockMovementRequest(
                        ingredientId,
                        InventoryMovementType.WASTE,
                        BigDecimal.valueOf(5),
                        "kg",
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(InventoryDomainException.class)
        .extracting("code")
        .isEqualTo(InventoryDomainException.STOCK_INSUFFICIENT);

    verify(balanceRepository, never()).save(any());
    verify(movementRepository, never()).save(any());
  }

  @Test
  void adjustmentOutReducesBalanceWithinBaseUnit() {
    UUID ingredientId = UUID.randomUUID();
    IngredientEntity ingredient = ingredient(ingredientId, "g");
    when(ingredientRepository.findById(ingredientId)).thenReturn(Optional.of(ingredient));
    InventoryStockBalanceEntity balance = balance(ingredient, BigDecimal.valueOf(1000), null);
    when(balanceRepository.findByIngredient_IdAndLocationCode(ingredientId, "DEFAULT"))
        .thenReturn(Optional.of(balance));

    StockMovementResponse response =
        service.recordMovement(
            null,
            new StockMovementRequest(
                ingredientId,
                InventoryMovementType.ADJUSTMENT_OUT,
                BigDecimal.valueOf(250),
                "g",
                null,
                null,
                null,
                null));

    assertThat(response.baseQuantityDelta()).isEqualByComparingTo("-250.000000");
    assertThat(response.resultingBalance()).isEqualByComparingTo("750.000000");
    assertThat(balance.getQuantityOnHand()).isEqualByComparingTo("750.000000");
  }

  @Test
  void stockCountSetsBalanceToCountedQuantityEvenBelowCurrent() {
    UUID ingredientId = UUID.randomUUID();
    IngredientEntity ingredient = ingredient(ingredientId, "g");
    when(ingredientRepository.findById(ingredientId)).thenReturn(Optional.of(ingredient));
    InventoryStockBalanceEntity balance = balance(ingredient, BigDecimal.valueOf(1000), null);
    when(balanceRepository.findByIngredient_IdAndLocationCode(ingredientId, "DEFAULT"))
        .thenReturn(Optional.of(balance));

    StockMovementResponse response =
        service.recordMovement(
            null,
            new StockMovementRequest(
                ingredientId,
                InventoryMovementType.STOCK_COUNT,
                BigDecimal.valueOf(300),
                "g",
                "count",
                null,
                null,
                null));

    assertThat(response.resultingBalance()).isEqualByComparingTo("300.000000");
    assertThat(response.baseQuantityDelta()).isEqualByComparingTo("-700.000000");
  }

  @Test
  void reservationReleaseIsRejectedAndBalanceUntouched() {
    // CR-03 regression: RESERVATION_RELEASE is neither inbound, outbound, nor a count -- it must
    // be rejected outright rather than silently falling into the STOCK_COUNT absolute-set path
    // and overwriting quantityOnHand with whatever quantity a staff member submits.
    UUID ingredientId = UUID.randomUUID();
    IngredientEntity ingredient = ingredient(ingredientId, "g");
    when(ingredientRepository.findById(ingredientId)).thenReturn(Optional.of(ingredient));
    InventoryStockBalanceEntity balance = balance(ingredient, BigDecimal.valueOf(1000), null);
    when(balanceRepository.findByIngredient_IdAndLocationCode(ingredientId, "DEFAULT"))
        .thenReturn(Optional.of(balance));

    assertThatThrownBy(
            () ->
                service.recordMovement(
                    null,
                    new StockMovementRequest(
                        ingredientId,
                        InventoryMovementType.RESERVATION_RELEASE,
                        BigDecimal.valueOf(300),
                        "g",
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(InventoryDomainException.class)
        .extracting("code")
        .isEqualTo(InventoryDomainException.MOVEMENT_INVALID);

    assertThat(balance.getQuantityOnHand()).isEqualByComparingTo("1000");
    verify(balanceRepository, never()).save(any());
    verify(movementRepository, never()).save(any());
  }

  @Test
  void nonPositiveQuantityIsRejected() {
    UUID ingredientId = UUID.randomUUID();
    IngredientEntity ingredient = ingredient(ingredientId, "g");
    when(ingredientRepository.findById(ingredientId)).thenReturn(Optional.of(ingredient));

    assertThatThrownBy(
            () ->
                service.recordMovement(
                    null,
                    new StockMovementRequest(
                        ingredientId,
                        InventoryMovementType.RECEIPT,
                        BigDecimal.ZERO,
                        "g",
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(InventoryDomainException.class)
        .extracting("code")
        .isEqualTo(InventoryDomainException.MOVEMENT_QUANTITY_INVALID);
  }

  @Test
  void archivedIngredientCannotReceiveMovements() {
    UUID ingredientId = UUID.randomUUID();
    IngredientEntity ingredient = ingredient(ingredientId, "g");
    ingredient.setStatus(IngredientStatus.ARCHIVED);
    when(ingredientRepository.findById(ingredientId)).thenReturn(Optional.of(ingredient));

    assertThatThrownBy(
            () ->
                service.recordMovement(
                    null,
                    new StockMovementRequest(
                        ingredientId,
                        InventoryMovementType.RECEIPT,
                        BigDecimal.TEN,
                        "g",
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(InventoryDomainException.class)
        .extracting("code")
        .isEqualTo(InventoryDomainException.INGREDIENT_NOT_ACTIVE);
  }

  @Test
  void lowStockThresholdMarksBalanceAsLow() {
    UUID ingredientId = UUID.randomUUID();
    IngredientEntity ingredient = ingredient(ingredientId, "g");
    when(ingredientRepository.findById(ingredientId)).thenReturn(Optional.of(ingredient));
    InventoryStockBalanceEntity balance =
        balance(ingredient, BigDecimal.valueOf(50), BigDecimal.valueOf(100));
    when(balanceRepository.findByIngredient_IdAndLocationCode(ingredientId, "DEFAULT"))
        .thenReturn(Optional.of(balance));

    StockBalanceResponse response = service.getStock(ingredientId);

    assertThat(response.lowStock()).isTrue();
    assertThat(response.lowStockThreshold()).isEqualByComparingTo("100");
  }

  @Test
  void missingBalanceForKnownIngredientReportsStockNotFound() {
    UUID ingredientId = UUID.randomUUID();
    when(ingredientRepository.findById(ingredientId))
        .thenReturn(Optional.of(ingredient(ingredientId, "g")));
    when(balanceRepository.findByIngredient_IdAndLocationCode(ingredientId, "DEFAULT"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getStock(ingredientId))
        .isInstanceOf(InventoryDomainException.class)
        .extracting("code")
        .isEqualTo(InventoryDomainException.STOCK_NOT_FOUND);
  }

  private IngredientEntity ingredient(UUID ingredientId, String baseUnit) {
    IngredientEntity ingredient = new IngredientEntity();
    ingredient.setId(ingredientId);
    ingredient.setName("Sugar");
    ingredient.setBaseUnit(baseUnit);
    ingredient.setStatus(IngredientStatus.ACTIVE);
    ingredient.setCreatedAt(Instant.now());
    return ingredient;
  }

  private InventoryStockBalanceEntity balance(
      IngredientEntity ingredient, BigDecimal quantityOnHand, BigDecimal threshold) {
    InventoryStockBalanceEntity balance = new InventoryStockBalanceEntity();
    balance.setId(UUID.randomUUID());
    balance.setIngredient(ingredient);
    balance.setLocationCode("DEFAULT");
    balance.setBaseUnit(ingredient.getBaseUnit());
    balance.setQuantityOnHand(quantityOnHand);
    balance.setLowStockThreshold(threshold);
    balance.setCreatedAt(Instant.now());
    return balance;
  }
}
