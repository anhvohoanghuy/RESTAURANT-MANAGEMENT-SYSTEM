package com.example.feat1.DDD.inventory_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.inventory_context.application.event.SettleTriggerEvent;
import com.example.feat1.DDD.inventory_context.domain.model.IngredientStatus;
import com.example.feat1.DDD.inventory_context.domain.model.InventoryDomainException;
import com.example.feat1.DDD.inventory_context.domain.model.InventoryMovementType;
import com.example.feat1.DDD.inventory_context.domain.port.MenuRecipeCostingPort;
import com.example.feat1.DDD.inventory_context.domain.port.OrderLineLookupPort;
import com.example.feat1.DDD.inventory_context.domain.service.RecipeRequirementResolver;
import com.example.feat1.DDD.inventory_context.domain.snapshot.OrderLineRecipeSnapshot;
import com.example.feat1.DDD.inventory_context.domain.snapshot.RecipeCostingSnapshot;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.IngredientEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryProcessedEventEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryStockBalanceEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryStockMovementEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.StockReservationEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.StockReservationEntity.ReservationStatus;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.IngredientRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryLineSettlementRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryProcessedEventRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryStockBalanceRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryStockMovementRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.StockReservationRepository;
import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

class InventoryReservationSettlementServiceTest {
  private static final String DEFAULT = InventoryStockBalanceEntity.DEFAULT_LOCATION;

  private InventoryProcessedEventRepository processedEventRepository;
  private InventoryLineSettlementRepository lineSettlementRepository;
  private StockReservationRepository reservationRepository;
  private InventoryStockBalanceRepository balanceRepository;
  private InventoryStockMovementRepository movementRepository;
  private OrderLineLookupPort orderLineLookupPort;
  private MenuRecipeCostingPort menuRecipeCostingPort;
  private IngredientRepository ingredientRepository;
  private InventoryReservationSettlementService service;

  @BeforeEach
  void setUp() {
    processedEventRepository = mock(InventoryProcessedEventRepository.class);
    lineSettlementRepository = mock(InventoryLineSettlementRepository.class);
    reservationRepository = mock(StockReservationRepository.class);
    balanceRepository = mock(InventoryStockBalanceRepository.class);
    movementRepository = mock(InventoryStockMovementRepository.class);
    orderLineLookupPort = mock(OrderLineLookupPort.class);
    menuRecipeCostingPort = mock(MenuRecipeCostingPort.class);
    ingredientRepository = mock(IngredientRepository.class);
    // Real resolver so recipe re-resolution is exercised through the shared collaborator (D-02).
    RecipeRequirementResolver resolver =
        new RecipeRequirementResolver(menuRecipeCostingPort, ingredientRepository);
    service =
        new InventoryReservationSettlementService(
            processedEventRepository,
            lineSettlementRepository,
            reservationRepository,
            balanceRepository,
            movementRepository,
            orderLineLookupPort,
            resolver);

    // Defaults: not processed, not settled, saves echo.
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
    when(lineSettlementRepository.existsByOrderIdAndOrderLineId(any(), any())).thenReturn(false);
    when(processedEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(balanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(lineSettlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void singleLineRecipeReResolvedAndDeducted() {
    UUID orderId = UUID.randomUUID();
    UUID orderLineId = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    UUID ingredientA = UUID.randomUUID();

    stubLine(orderId, orderLineId, dishId, 2, List.of());
    stubRecipe(RecipeTargetType.DISH, dishId, recipeLine(ingredientA, "3", "g"));
    stubIngredient(ingredientA, "g");
    InventoryStockBalanceEntity balanceA =
        balance(ingredientA, "g", BigDecimal.valueOf(50), BigDecimal.valueOf(20));
    when(balanceRepository.lockByIngredientAndLocation(ingredientA, DEFAULT))
        .thenReturn(Optional.of(balanceA));
    heldReservation(orderId);
    when(lineSettlementRepository.countByOrderId(orderId)).thenReturn(1L);

    service.onSettleTrigger(event(orderId, orderLineId, 1));

    // need = 3 x 2 = 6; on_hand 50 - 6 = 44; reserved 20 - 6 = 14.
    assertThat(balanceA.getQuantityOnHand()).isEqualByComparingTo("44");
    assertThat(balanceA.getReservedQuantity()).isEqualByComparingTo("14");
  }

  @Test
  void clampsOnHandToZeroAndLogsAnomaly() {
    UUID orderId = UUID.randomUUID();
    UUID orderLineId = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    UUID ingredientA = UUID.randomUUID();

    stubLine(orderId, orderLineId, dishId, 1, List.of());
    stubRecipe(RecipeTargetType.DISH, dishId, recipeLine(ingredientA, "5", "g"));
    stubIngredient(ingredientA, "g");
    // on_hand 3 < need 5, reserved 2 < need 5 -> both clamp to 0, no throw.
    InventoryStockBalanceEntity balanceA =
        balance(ingredientA, "g", BigDecimal.valueOf(3), BigDecimal.valueOf(2));
    when(balanceRepository.lockByIngredientAndLocation(ingredientA, DEFAULT))
        .thenReturn(Optional.of(balanceA));
    heldReservation(orderId);
    when(lineSettlementRepository.countByOrderId(orderId)).thenReturn(1L);

    assertThatCode(() -> service.onSettleTrigger(event(orderId, orderLineId, 1)))
        .doesNotThrowAnyException();

    assertThat(balanceA.getQuantityOnHand()).isEqualByComparingTo("0");
    assertThat(balanceA.getReservedQuantity()).isEqualByComparingTo("0");
  }

  @Test
  void recordsConsumptionMovementPerIngredient() {
    UUID orderId = UUID.randomUUID();
    UUID orderLineId = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    UUID ingredientA = UUID.randomUUID();

    stubLine(orderId, orderLineId, dishId, 1, List.of());
    stubRecipe(RecipeTargetType.DISH, dishId, recipeLine(ingredientA, "4", "g"));
    stubIngredient(ingredientA, "g");
    InventoryStockBalanceEntity balanceA =
        balance(ingredientA, "g", BigDecimal.valueOf(10), BigDecimal.valueOf(4));
    when(balanceRepository.lockByIngredientAndLocation(ingredientA, DEFAULT))
        .thenReturn(Optional.of(balanceA));
    heldReservation(orderId);
    when(lineSettlementRepository.countByOrderId(orderId)).thenReturn(1L);

    service.onSettleTrigger(event(orderId, orderLineId, 1));

    ArgumentCaptor<InventoryStockMovementEntity> captor =
        ArgumentCaptor.forClass(InventoryStockMovementEntity.class);
    verify(movementRepository).save(captor.capture());
    InventoryStockMovementEntity movement = captor.getValue();
    assertThat(movement.getMovementType()).isEqualTo(InventoryMovementType.CONSUMPTION);
    assertThat(movement.getReferenceType()).isEqualTo("ORDER_LINE");
    assertThat(movement.getReferenceId()).isEqualTo(orderLineId);
    assertThat(movement.getQuantity()).isEqualByComparingTo("4");
    assertThat(movement.getBaseQuantityDelta()).isEqualByComparingTo("-4");
    assertThat(movement.getResultingBalance()).isEqualByComparingTo("6");
  }

  @Test
  void recordsProcessedEventAfterSuccessfulSettlement() {
    UUID orderId = UUID.randomUUID();
    UUID orderLineId = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    UUID ingredientA = UUID.randomUUID();
    SettleTriggerEvent event = event(orderId, orderLineId, 1);

    stubLine(orderId, orderLineId, dishId, 1, List.of());
    stubRecipe(RecipeTargetType.DISH, dishId, recipeLine(ingredientA, "4", "g"));
    stubIngredient(ingredientA, "g");
    InventoryStockBalanceEntity balanceA =
        balance(ingredientA, "g", BigDecimal.valueOf(10), BigDecimal.valueOf(4));
    when(balanceRepository.lockByIngredientAndLocation(ingredientA, DEFAULT))
        .thenReturn(Optional.of(balanceA));
    heldReservation(orderId);
    when(lineSettlementRepository.countByOrderId(orderId)).thenReturn(1L);

    service.onSettleTrigger(event);

    ArgumentCaptor<InventoryProcessedEventEntity> captor =
        ArgumentCaptor.forClass(InventoryProcessedEventEntity.class);
    verify(processedEventRepository).save(captor.capture());
    InventoryProcessedEventEntity ledger = captor.getValue();
    assertThat(ledger.getEventId()).isEqualTo(event.eventId());
    assertThat(ledger.getConsumerName())
        .isEqualTo(InventoryReservationSettlementService.CONSUMER_NAME);

    InOrder inOrder = inOrder(lineSettlementRepository, processedEventRepository);
    inOrder.verify(lineSettlementRepository).save(any());
    inOrder.verify(processedEventRepository).save(any());
  }

  @Test
  void finalLedgerInsertPropagatesToRollBackWholeSettlementTransaction() {
    UUID orderId = UUID.randomUUID();
    UUID orderLineId = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    UUID ingredientA = UUID.randomUUID();

    stubLine(orderId, orderLineId, dishId, 1, List.of());
    stubRecipe(RecipeTargetType.DISH, dishId, recipeLine(ingredientA, "4", "g"));
    stubIngredient(ingredientA, "g");
    InventoryStockBalanceEntity balanceA =
        balance(ingredientA, "g", BigDecimal.valueOf(10), BigDecimal.valueOf(4));
    when(balanceRepository.lockByIngredientAndLocation(ingredientA, DEFAULT))
        .thenReturn(Optional.of(balanceA));
    heldReservation(orderId);
    when(lineSettlementRepository.countByOrderId(orderId)).thenReturn(1L);
    when(processedEventRepository.save(any()))
        .thenThrow(new DataIntegrityViolationException("duplicate ledger"));

    assertThatThrownBy(() -> service.onSettleTrigger(event(orderId, orderLineId, 1)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void marksSettledOnlyWhenLastLineSettles() {
    UUID orderId = UUID.randomUUID();
    UUID lineOne = UUID.randomUUID();
    UUID lineTwo = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    UUID ingredientA = UUID.randomUUID();

    stubLine(orderId, lineOne, dishId, 1, List.of());
    stubLine(orderId, lineTwo, dishId, 1, List.of());
    stubRecipe(RecipeTargetType.DISH, dishId, recipeLine(ingredientA, "1", "g"));
    stubIngredient(ingredientA, "g");
    InventoryStockBalanceEntity balanceA =
        balance(ingredientA, "g", BigDecimal.valueOf(100), BigDecimal.valueOf(100));
    when(balanceRepository.lockByIngredientAndLocation(ingredientA, DEFAULT))
        .thenReturn(Optional.of(balanceA));
    StockReservationEntity reservation = heldReservation(orderId);
    when(lineSettlementRepository.countByOrderId(orderId)).thenReturn(1L, 2L);

    service.onSettleTrigger(event(orderId, lineOne, 2));
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.HELD);

    service.onSettleTrigger(event(orderId, lineTwo, 2));
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.SETTLED);
  }

  @Test
  void outOfOrderLastLineStillFlipsExactlyOnce() {
    UUID orderId = UUID.randomUUID();
    UUID lineOne = UUID.randomUUID();
    UUID lineTwo = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    UUID ingredientA = UUID.randomUUID();

    stubLine(orderId, lineOne, dishId, 1, List.of());
    stubLine(orderId, lineTwo, dishId, 1, List.of());
    stubRecipe(RecipeTargetType.DISH, dishId, recipeLine(ingredientA, "1", "g"));
    stubIngredient(ingredientA, "g");
    InventoryStockBalanceEntity balanceA =
        balance(ingredientA, "g", BigDecimal.valueOf(100), BigDecimal.valueOf(100));
    when(balanceRepository.lockByIngredientAndLocation(ingredientA, DEFAULT))
        .thenReturn(Optional.of(balanceA));
    StockReservationEntity reservation = heldReservation(orderId);
    when(lineSettlementRepository.countByOrderId(orderId)).thenReturn(1L, 2L);

    // Settle line 2 first, then line 1 — count-based flip is positional-agnostic.
    service.onSettleTrigger(event(orderId, lineTwo, 2));
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.HELD);

    service.onSettleTrigger(event(orderId, lineOne, 2));
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.SETTLED);
  }

  @Test
  void duplicateEventIdSkips() {
    UUID orderId = UUID.randomUUID();
    UUID orderLineId = UUID.randomUUID();
    SettleTriggerEvent event = event(orderId, orderLineId, 1);
    when(processedEventRepository.existsByEventIdAndConsumerName(
            event.eventId(), InventoryReservationSettlementService.CONSUMER_NAME))
        .thenReturn(true);

    service.onSettleTrigger(event);

    verify(processedEventRepository, never()).save(any());
    verify(reservationRepository, never()).lockByOrderId(any());
    verify(balanceRepository, never()).lockByIngredientAndLocation(any(), any());
    verify(movementRepository, never()).save(any());
  }

  @Test
  void duplicateOrderLineSkipsEvenWithNewEventId() {
    UUID orderId = UUID.randomUUID();
    UUID orderLineId = UUID.randomUUID();
    when(lineSettlementRepository.existsByOrderIdAndOrderLineId(orderId, orderLineId))
        .thenReturn(true);

    service.onSettleTrigger(event(orderId, orderLineId, 1));

    verify(processedEventRepository, never()).save(any());
    verify(reservationRepository, never()).lockByOrderId(any());
    verify(movementRepository, never()).save(any());
  }

  @Test
  void missingReservationThrowsForDltRouting() {
    UUID orderId = UUID.randomUUID();
    UUID orderLineId = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    UUID ingredientA = UUID.randomUUID();

    stubLine(orderId, orderLineId, dishId, 1, List.of());
    stubRecipe(RecipeTargetType.DISH, dishId, recipeLine(ingredientA, "1", "g"));
    stubIngredient(ingredientA, "g");
    when(reservationRepository.lockByOrderId(orderId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.onSettleTrigger(event(orderId, orderLineId, 1)))
        .isInstanceOf(InventoryDomainException.class);

    verify(movementRepository, never()).save(any());
    verify(lineSettlementRepository, never()).save(any());
  }

  @Test
  void alreadySettledReservationIsBenign() {
    UUID orderId = UUID.randomUUID();
    UUID orderLineId = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    UUID ingredientA = UUID.randomUUID();

    stubLine(orderId, orderLineId, dishId, 1, List.of());
    stubRecipe(RecipeTargetType.DISH, dishId, recipeLine(ingredientA, "1", "g"));
    stubIngredient(ingredientA, "g");
    StockReservationEntity reservation = heldReservation(orderId);
    reservation.setStatus(ReservationStatus.SETTLED);

    assertThatCode(() -> service.onSettleTrigger(event(orderId, orderLineId, 1)))
        .doesNotThrowAnyException();

    verify(balanceRepository, never()).lockByIngredientAndLocation(any(), any());
    verify(movementRepository, never()).save(any());
    verify(lineSettlementRepository, never()).save(any());
  }

  @Test
  void locksReservationBeforeBalances() {
    UUID orderId = UUID.randomUUID();
    UUID orderLineId = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    UUID ingredientA = UUID.randomUUID();

    stubLine(orderId, orderLineId, dishId, 1, List.of());
    stubRecipe(RecipeTargetType.DISH, dishId, recipeLine(ingredientA, "1", "g"));
    stubIngredient(ingredientA, "g");
    InventoryStockBalanceEntity balanceA =
        balance(ingredientA, "g", BigDecimal.valueOf(10), BigDecimal.valueOf(10));
    when(balanceRepository.lockByIngredientAndLocation(ingredientA, DEFAULT))
        .thenReturn(Optional.of(balanceA));
    heldReservation(orderId);
    when(lineSettlementRepository.countByOrderId(orderId)).thenReturn(1L);

    service.onSettleTrigger(event(orderId, orderLineId, 1));

    InOrder inOrder = inOrder(reservationRepository, balanceRepository);
    inOrder.verify(reservationRepository).lockByOrderId(orderId);
    inOrder.verify(balanceRepository).lockByIngredientAndLocation(eq(ingredientA), eq(DEFAULT));
  }

  // ---- fixtures -------------------------------------------------------------

  private SettleTriggerEvent event(UUID orderId, UUID orderLineId, int totalLines) {
    return new SettleTriggerEvent(
        UUID.randomUUID(),
        SettleTriggerEvent.TYPE,
        Instant.now(),
        orderId,
        orderLineId,
        totalLines);
  }

  private void stubLine(
      UUID orderId, UUID orderLineId, UUID dishId, int quantity, List<UUID> toppingOptionIds) {
    when(orderLineLookupPort.findLine(orderId, orderLineId))
        .thenReturn(
            Optional.of(
                new OrderLineRecipeSnapshot(orderLineId, dishId, quantity, toppingOptionIds)));
  }

  private StockReservationEntity heldReservation(UUID orderId) {
    StockReservationEntity reservation = new StockReservationEntity();
    reservation.setId(UUID.randomUUID());
    reservation.setOrderId(orderId);
    reservation.setStatus(ReservationStatus.HELD);
    reservation.setCreatedAt(Instant.now());
    when(reservationRepository.lockByOrderId(orderId)).thenReturn(Optional.of(reservation));
    return reservation;
  }

  private void stubRecipe(
      RecipeTargetType targetType, UUID targetId, RecipeCostingSnapshot.Line... lines) {
    RecipeCostingSnapshot snapshot =
        new RecipeCostingSnapshot(targetType, targetId, "recipe", List.of(lines));
    when(menuRecipeCostingPort.findRecipe(targetType, targetId)).thenReturn(Optional.of(snapshot));
  }

  private RecipeCostingSnapshot.Line recipeLine(UUID ingredientId, String qty, String unit) {
    return new RecipeCostingSnapshot.Line(
        UUID.randomUUID(), ingredientId, "ingredient", new BigDecimal(qty), unit, 0);
  }

  private void stubIngredient(UUID ingredientId, String baseUnit) {
    when(ingredientRepository.findById(ingredientId))
        .thenReturn(Optional.of(ingredient(ingredientId, baseUnit)));
  }

  private IngredientEntity ingredient(UUID ingredientId, String baseUnit) {
    IngredientEntity ingredient = new IngredientEntity();
    ingredient.setId(ingredientId);
    ingredient.setName("Ingredient " + ingredientId);
    ingredient.setBaseUnit(baseUnit);
    ingredient.setStatus(IngredientStatus.ACTIVE);
    ingredient.setCreatedAt(Instant.now());
    return ingredient;
  }

  private InventoryStockBalanceEntity balance(
      UUID ingredientId, String baseUnit, BigDecimal onHand, BigDecimal reserved) {
    InventoryStockBalanceEntity balance = new InventoryStockBalanceEntity();
    balance.setId(UUID.randomUUID());
    balance.setIngredient(ingredient(ingredientId, baseUnit));
    balance.setLocationCode(DEFAULT);
    balance.setBaseUnit(baseUnit);
    balance.setQuantityOnHand(onHand);
    balance.setReservedQuantity(reserved);
    balance.setCreatedAt(Instant.now());
    return balance;
  }
}
