package com.example.feat1.DDD.inventory_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.inventory_context.domain.model.IngredientStatus;
import com.example.feat1.DDD.inventory_context.domain.port.InventoryStockResultPublisher;
import com.example.feat1.DDD.inventory_context.domain.port.MenuRecipeCostingPort;
import com.example.feat1.DDD.inventory_context.domain.snapshot.RecipeCostingSnapshot;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.IngredientEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryStockBalanceEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.StockReservationEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.IngredientRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryProcessedEventRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryStockBalanceRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.StockReservationRepository;
import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import com.example.feat1.DDD.order_context.application.event.OrderCreatedEvent;
import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent;
import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent.Result;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class InventoryReservationServiceTest {
  private static final String DEFAULT = InventoryStockBalanceEntity.DEFAULT_LOCATION;

  private InventoryProcessedEventRepository processedEventRepository;
  private StockReservationRepository reservationRepository;
  private InventoryStockBalanceRepository balanceRepository;
  private IngredientRepository ingredientRepository;
  private MenuRecipeCostingPort menuRecipeCostingPort;
  private InventoryStockResultPublisher stockResultPublisher;
  private InventoryReservationService service;

  @BeforeEach
  void setUp() {
    processedEventRepository = mock(InventoryProcessedEventRepository.class);
    reservationRepository = mock(StockReservationRepository.class);
    balanceRepository = mock(InventoryStockBalanceRepository.class);
    ingredientRepository = mock(IngredientRepository.class);
    menuRecipeCostingPort = mock(MenuRecipeCostingPort.class);
    stockResultPublisher = mock(InventoryStockResultPublisher.class);
    service =
        new InventoryReservationService(
            processedEventRepository,
            reservationRepository,
            balanceRepository,
            ingredientRepository,
            menuRecipeCostingPort,
            stockResultPublisher);
    // Defaults: not yet processed, no reservation, saves echo their argument.
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
    when(reservationRepository.existsByOrderId(any())).thenReturn(false);
    when(processedEventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    when(balanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void sufficientStockReservesAndConfirms() {
    UUID ingredientA = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    OrderCreatedEvent event = event(line(dishId, 1));

    stubRecipe(RecipeTargetType.DISH, dishId, recipeLine(ingredientA, "5", "g"));
    stubIngredient(ingredientA, "g");
    InventoryStockBalanceEntity balanceA =
        balance(ingredientA, "g", BigDecimal.valueOf(10), BigDecimal.valueOf(2));
    when(balanceRepository.lockByIngredientAndLocation(ingredientA, DEFAULT))
        .thenReturn(Optional.of(balanceA));

    service.onOrderCreated(event);

    // reserved 2 + required 5 = 7; available stays 10 - 7 = 3 >= 0 (D-02).
    assertThat(balanceA.getReservedQuantity()).isEqualByComparingTo("7");
    verify(balanceRepository).save(balanceA);

    ArgumentCaptor<StockReservationEntity> reservationCaptor =
        ArgumentCaptor.forClass(StockReservationEntity.class);
    verify(reservationRepository).save(reservationCaptor.capture());
    assertThat(reservationCaptor.getValue().getOrderId()).isEqualTo(event.orderId());
    assertThat(reservationCaptor.getValue().getLines()).hasSize(1);

    OrderStockResultEvent result = capturePublished();
    assertThat(result.result()).isEqualTo(Result.CONFIRMED);
    assertThat(result.eventType()).isEqualTo(OrderStockResultEvent.CONFIRMED_TYPE);
    assertThat(result.orderId()).isEqualTo(event.orderId());
    assertThat(result.shortfalls()).isEmpty();
  }

  @Test
  void insufficientStockRejectsWithShortfallAndReservesNothing() {
    UUID ingredientA = UUID.randomUUID();
    UUID ingredientB = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    OrderCreatedEvent event = event(line(dishId, 1));

    stubRecipe(
        RecipeTargetType.DISH,
        dishId,
        recipeLine(ingredientA, "5", "g"),
        recipeLine(ingredientB, "9", "g"));
    stubIngredient(ingredientA, "g");
    stubIngredient(ingredientB, "g");
    InventoryStockBalanceEntity balanceA =
        balance(ingredientA, "g", BigDecimal.valueOf(10), BigDecimal.valueOf(2)); // available 8 ok
    InventoryStockBalanceEntity balanceB =
        balance(ingredientB, "g", BigDecimal.valueOf(4), BigDecimal.ZERO); // available 4 < 9
    when(balanceRepository.lockByIngredientAndLocation(ingredientA, DEFAULT))
        .thenReturn(Optional.of(balanceA));
    when(balanceRepository.lockByIngredientAndLocation(ingredientB, DEFAULT))
        .thenReturn(Optional.of(balanceB));

    service.onOrderCreated(event);

    // No reservation, no balance mutation when any ingredient is short.
    verify(reservationRepository, never()).save(any());
    verify(balanceRepository, never()).save(any());
    assertThat(balanceA.getReservedQuantity()).isEqualByComparingTo("2");
    assertThat(balanceB.getReservedQuantity()).isEqualByComparingTo("0");

    OrderStockResultEvent result = capturePublished();
    assertThat(result.result()).isEqualTo(Result.REJECTED);
    assertThat(result.eventType()).isEqualTo(OrderStockResultEvent.REJECTED_TYPE);
    assertThat(result.shortfalls()).hasSize(1);
    OrderStockResultEvent.Shortfall shortfall = result.shortfalls().get(0);
    assertThat(shortfall.ingredientId()).isEqualTo(ingredientB);
    assertThat(shortfall.required()).isEqualByComparingTo("9");
    assertThat(shortfall.available()).isEqualByComparingTo("4");
  }

  @Test
  void resolvesDishAndToppingRecipesAndTreatsMissingRecipeAsZero() {
    UUID ingredientA = UUID.randomUUID();
    UUID ingredientB = UUID.randomUUID();
    UUID dishWithRecipe = UUID.randomUUID();
    UUID dishWithoutRecipe = UUID.randomUUID();
    UUID toppingOptionId = UUID.randomUUID();

    // Line 1: dish recipe (A x3) + topping recipe (B x2), order quantity 2 -> A=6, B=4.
    OrderCreatedEvent.OrderLine line1 =
        new OrderCreatedEvent.OrderLine(
            UUID.randomUUID(),
            dishWithRecipe,
            "Latte",
            BigDecimal.TEN,
            List.of(topping(toppingOptionId)),
            BigDecimal.ZERO,
            BigDecimal.TEN,
            2,
            BigDecimal.valueOf(20));
    // Line 2: dish with no recipe -> contributes zero, must not block confirmation.
    OrderCreatedEvent.OrderLine line2 = line(dishWithoutRecipe, 1);
    OrderCreatedEvent event = event(line1, line2);

    stubRecipe(RecipeTargetType.DISH, dishWithRecipe, recipeLine(ingredientA, "3", "g"));
    stubRecipe(RecipeTargetType.TOPPING_OPTION, toppingOptionId, recipeLine(ingredientB, "2", "g"));
    when(menuRecipeCostingPort.findRecipe(RecipeTargetType.DISH, dishWithoutRecipe))
        .thenReturn(Optional.empty());
    stubIngredient(ingredientA, "g");
    stubIngredient(ingredientB, "g");
    InventoryStockBalanceEntity balanceA =
        balance(ingredientA, "g", BigDecimal.valueOf(100), BigDecimal.ZERO);
    InventoryStockBalanceEntity balanceB =
        balance(ingredientB, "g", BigDecimal.valueOf(100), BigDecimal.ZERO);
    when(balanceRepository.lockByIngredientAndLocation(ingredientA, DEFAULT))
        .thenReturn(Optional.of(balanceA));
    when(balanceRepository.lockByIngredientAndLocation(ingredientB, DEFAULT))
        .thenReturn(Optional.of(balanceB));

    service.onOrderCreated(event);

    assertThat(balanceA.getReservedQuantity()).isEqualByComparingTo("6"); // 3 x 2
    assertThat(balanceB.getReservedQuantity()).isEqualByComparingTo("4"); // 2 x 2
    OrderStockResultEvent result = capturePublished();
    assertThat(result.result()).isEqualTo(Result.CONFIRMED);
    assertThat(result.shortfalls()).isEmpty();
  }

  @Test
  void alreadyProcessedEventIsSkippedWithNoSideEffects() {
    UUID dishId = UUID.randomUUID();
    OrderCreatedEvent event = event(line(dishId, 1));
    when(processedEventRepository.existsByEventIdAndConsumerName(
            event.eventId(), InventoryReservationService.CONSUMER_NAME))
        .thenReturn(true);

    service.onOrderCreated(event);

    verify(processedEventRepository, never()).saveAndFlush(any());
    verify(balanceRepository, never()).lockByIngredientAndLocation(any(), any());
    verify(reservationRepository, never()).save(any());
    verify(stockResultPublisher, never()).publishStockResult(any());
  }

  @Test
  void existingReservationForOrderIsSkippedWithNoSideEffects() {
    UUID dishId = UUID.randomUUID();
    OrderCreatedEvent event = event(line(dishId, 1));
    when(reservationRepository.existsByOrderId(event.orderId())).thenReturn(true);

    service.onOrderCreated(event);

    verify(processedEventRepository, never()).saveAndFlush(any());
    verify(balanceRepository, never()).lockByIngredientAndLocation(any(), any());
    verify(reservationRepository, never()).save(any());
    verify(stockResultPublisher, never()).publishStockResult(any());
  }

  @Test
  void locksBalancesInAscendingIngredientIdOrder() {
    // Fixed UUIDs so ascending order is deterministic: low < high.
    UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID high = UUID.fromString("00000000-0000-0000-0000-000000000002");
    UUID dishId = UUID.randomUUID();
    OrderCreatedEvent event = event(line(dishId, 1));

    // Recipe lists high before low; the service must still lock low first (canonical order).
    stubRecipe(
        RecipeTargetType.DISH, dishId, recipeLine(high, "1", "g"), recipeLine(low, "1", "g"));
    stubIngredient(low, "g");
    stubIngredient(high, "g");
    when(balanceRepository.lockByIngredientAndLocation(low, DEFAULT))
        .thenReturn(Optional.of(balance(low, "g", BigDecimal.valueOf(100), BigDecimal.ZERO)));
    when(balanceRepository.lockByIngredientAndLocation(high, DEFAULT))
        .thenReturn(Optional.of(balance(high, "g", BigDecimal.valueOf(100), BigDecimal.ZERO)));

    service.onOrderCreated(event);

    InOrder inOrder = inOrder(balanceRepository);
    inOrder.verify(balanceRepository).lockByIngredientAndLocation(eq(low), eq(DEFAULT));
    inOrder.verify(balanceRepository).lockByIngredientAndLocation(eq(high), eq(DEFAULT));
  }

  // ---- fixtures -------------------------------------------------------------

  private OrderStockResultEvent capturePublished() {
    ArgumentCaptor<OrderStockResultEvent> captor =
        ArgumentCaptor.forClass(OrderStockResultEvent.class);
    verify(stockResultPublisher).publishStockResult(captor.capture());
    return captor.getValue();
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

  private OrderCreatedEvent.OrderTopping topping(UUID toppingOptionId) {
    return new OrderCreatedEvent.OrderTopping(
        UUID.randomUUID(), "group", toppingOptionId, "option", BigDecimal.ZERO);
  }

  private OrderCreatedEvent.OrderLine line(UUID dishId, int quantity) {
    return new OrderCreatedEvent.OrderLine(
        UUID.randomUUID(),
        dishId,
        "dish",
        BigDecimal.TEN,
        List.of(),
        BigDecimal.ZERO,
        BigDecimal.TEN,
        quantity,
        BigDecimal.TEN.multiply(BigDecimal.valueOf(quantity)));
  }

  private OrderCreatedEvent event(OrderCreatedEvent.OrderLine... lines) {
    return new OrderCreatedEvent(
        UUID.randomUUID(),
        OrderCreatedEvent.TYPE,
        Instant.now(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        new OrderCreatedEvent.OrderTable(
            UUID.randomUUID(), UUID.randomUUID(), "T1", "Table 1", UUID.randomUUID(), "Area"),
        List.of(lines),
        BigDecimal.TEN,
        Instant.now());
  }
}
