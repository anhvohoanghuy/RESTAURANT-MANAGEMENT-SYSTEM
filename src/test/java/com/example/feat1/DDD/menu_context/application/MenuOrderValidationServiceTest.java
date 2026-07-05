package com.example.feat1.DDD.menu_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.menu_context.application.dto.MenuSelectionDtos.MenuSelectionRequest;
import com.example.feat1.DDD.menu_context.domain.model.Dish;
import com.example.feat1.DDD.menu_context.domain.model.MenuCategory;
import com.example.feat1.DDD.menu_context.domain.model.MenuDomainException;
import com.example.feat1.DDD.menu_context.domain.model.MenuStatus;
import com.example.feat1.DDD.menu_context.domain.model.ToppingGroup;
import com.example.feat1.DDD.menu_context.domain.model.ToppingOption;
import com.example.feat1.DDD.menu_context.domain.repository.DishDomainRepository;
import com.example.feat1.DDD.menu_context.domain.repository.MenuCategoryDomainRepository;
import com.example.feat1.DDD.menu_context.domain.repository.ToppingGroupDomainRepository;
import com.example.feat1.DDD.menu_context.domain.repository.ToppingOptionDomainRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MenuOrderValidationServiceTest {
  private MenuCategoryDomainRepository categoryRepository;
  private DishDomainRepository dishRepository;
  private ToppingGroupDomainRepository toppingGroupRepository;
  private ToppingOptionDomainRepository toppingOptionRepository;
  private MenuOrderValidationService service;

  @BeforeEach
  void setUp() {
    categoryRepository = mock(MenuCategoryDomainRepository.class);
    dishRepository = mock(DishDomainRepository.class);
    toppingGroupRepository = mock(ToppingGroupDomainRepository.class);
    toppingOptionRepository = mock(ToppingOptionDomainRepository.class);
    service =
        new MenuOrderValidationService(
            categoryRepository, dishRepository, toppingGroupRepository, toppingOptionRepository);
  }

  @Test
  void validSelectionReturnsDishToppingSnapshotsAndTotalPrice() {
    UUID categoryId = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    UUID sizeGroupId = UUID.randomUUID();
    UUID toppingGroupId = UUID.randomUUID();
    UUID largeId = UUID.randomUUID();
    UUID pearlId = UUID.randomUUID();
    Dish dish = dish(dishId, categoryId, MenuStatus.ACTIVE, BigDecimal.valueOf(45_000));
    ToppingGroup sizeGroup = group(sizeGroupId, dishId, "Size", 1, 1, 1);
    ToppingGroup toppingGroup = group(toppingGroupId, dishId, "Topping", 0, 2, 2);
    ToppingOption large =
        option(largeId, sizeGroupId, "Large", BigDecimal.valueOf(5_000), MenuStatus.ACTIVE, 1);
    ToppingOption pearl =
        option(
            pearlId,
            toppingGroupId,
            "Black pearl",
            BigDecimal.valueOf(7_000),
            MenuStatus.ACTIVE,
            1);
    stubOrderableDish(categoryId, dishId, dish);
    when(toppingGroupRepository.findByDishIds(List.of(dishId)))
        .thenReturn(List.of(sizeGroup, toppingGroup));
    when(toppingOptionRepository.findById(largeId)).thenReturn(Optional.of(large));
    when(toppingOptionRepository.findById(pearlId)).thenReturn(Optional.of(pearl));

    var quote =
        service.validateAndQuote(new MenuSelectionRequest(dishId, List.of(largeId, pearlId)));

    assertThat(quote.dishId()).isEqualTo(dishId);
    assertThat(quote.dishName()).isEqualTo("Milk Tea");
    assertThat(quote.basePrice()).isEqualByComparingTo("45000");
    assertThat(quote.toppingsTotal()).isEqualByComparingTo("12000");
    assertThat(quote.totalPrice()).isEqualByComparingTo("57000");
    assertThat(quote.selectedToppings()).hasSize(2);
    assertThat(quote.selectedToppings())
        .extracting("toppingOptionName")
        .containsExactly("Large", "Black pearl");
  }

  @Test
  void inactiveDishReturnsStableDishNotOrderableCode() {
    UUID categoryId = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    when(dishRepository.findById(dishId))
        .thenReturn(Optional.of(dish(dishId, categoryId, MenuStatus.INACTIVE, BigDecimal.TEN)));

    assertMenuCode(
        () -> service.validateAndQuote(new MenuSelectionRequest(dishId, List.of())),
        MenuDomainException.DISH_NOT_ORDERABLE);
  }

  @Test
  void inactiveCategoryReturnsStableDishNotOrderableCode() {
    UUID categoryId = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    when(dishRepository.findById(dishId))
        .thenReturn(Optional.of(dish(dishId, categoryId, MenuStatus.ACTIVE, BigDecimal.TEN)));
    when(categoryRepository.findById(categoryId))
        .thenReturn(Optional.of(category(categoryId, MenuStatus.INACTIVE)));

    assertMenuCode(
        () -> service.validateAndQuote(new MenuSelectionRequest(dishId, List.of())),
        MenuDomainException.DISH_NOT_ORDERABLE);
  }

  @Test
  void missingSelectedOptionReturnsStableToppingNotOrderableCode() {
    UUID categoryId = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    UUID optionId = UUID.randomUUID();
    stubOrderableDish(
        categoryId, dishId, dish(dishId, categoryId, MenuStatus.ACTIVE, BigDecimal.TEN));
    when(toppingGroupRepository.findByDishIds(List.of(dishId))).thenReturn(List.of());
    when(toppingOptionRepository.findById(optionId)).thenReturn(Optional.empty());

    assertMenuCode(
        () -> service.validateAndQuote(new MenuSelectionRequest(dishId, List.of(optionId))),
        MenuDomainException.TOPPING_NOT_ORDERABLE);
  }

  @Test
  void inactiveSelectedOptionReturnsStableToppingNotOrderableCode() {
    UUID categoryId = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID optionId = UUID.randomUUID();
    stubOrderableDish(
        categoryId, dishId, dish(dishId, categoryId, MenuStatus.ACTIVE, BigDecimal.TEN));
    when(toppingGroupRepository.findByDishIds(List.of(dishId)))
        .thenReturn(List.of(group(groupId, dishId, "Sauce", 0, 1, 1)));
    when(toppingOptionRepository.findById(optionId))
        .thenReturn(
            Optional.of(
                option(optionId, groupId, "Sauce", BigDecimal.ONE, MenuStatus.INACTIVE, 1)));

    assertMenuCode(
        () -> service.validateAndQuote(new MenuSelectionRequest(dishId, List.of(optionId))),
        MenuDomainException.TOPPING_NOT_ORDERABLE);
  }

  @Test
  void optionFromAnotherDishReturnsStableToppingNotInDishCode() {
    UUID categoryId = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    UUID dishGroupId = UUID.randomUUID();
    UUID otherGroupId = UUID.randomUUID();
    UUID optionId = UUID.randomUUID();
    stubOrderableDish(
        categoryId, dishId, dish(dishId, categoryId, MenuStatus.ACTIVE, BigDecimal.TEN));
    when(toppingGroupRepository.findByDishIds(List.of(dishId)))
        .thenReturn(List.of(group(dishGroupId, dishId, "Sauce", 0, 1, 1)));
    when(toppingOptionRepository.findById(optionId))
        .thenReturn(
            Optional.of(
                option(optionId, otherGroupId, "Other", BigDecimal.ONE, MenuStatus.ACTIVE, 1)));

    assertMenuCode(
        () -> service.validateAndQuote(new MenuSelectionRequest(dishId, List.of(optionId))),
        MenuDomainException.TOPPING_NOT_IN_DISH);
  }

  @Test
  void missingRequiredGroupReturnsStableGroupRequiredCode() {
    UUID categoryId = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    stubOrderableDish(
        categoryId, dishId, dish(dishId, categoryId, MenuStatus.ACTIVE, BigDecimal.TEN));
    when(toppingGroupRepository.findByDishIds(List.of(dishId)))
        .thenReturn(List.of(group(groupId, dishId, "Size", 1, 1, 1)));

    assertMenuCode(
        () -> service.validateAndQuote(new MenuSelectionRequest(dishId, List.of())),
        MenuDomainException.TOPPING_GROUP_REQUIRED);
  }

  @Test
  void tooManySelectionsReturnsStableGroupLimitExceededCode() {
    UUID categoryId = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID firstOptionId = UUID.randomUUID();
    UUID secondOptionId = UUID.randomUUID();
    stubOrderableDish(
        categoryId, dishId, dish(dishId, categoryId, MenuStatus.ACTIVE, BigDecimal.TEN));
    when(toppingGroupRepository.findByDishIds(List.of(dishId)))
        .thenReturn(List.of(group(groupId, dishId, "Sauce", 0, 1, 1)));
    when(toppingOptionRepository.findById(firstOptionId))
        .thenReturn(
            Optional.of(
                option(firstOptionId, groupId, "Mayo", BigDecimal.ONE, MenuStatus.ACTIVE, 1)));
    when(toppingOptionRepository.findById(secondOptionId))
        .thenReturn(
            Optional.of(
                option(secondOptionId, groupId, "Chili", BigDecimal.ONE, MenuStatus.ACTIVE, 2)));

    assertMenuCode(
        () ->
            service.validateAndQuote(
                new MenuSelectionRequest(dishId, List.of(firstOptionId, secondOptionId))),
        MenuDomainException.TOPPING_GROUP_LIMIT_EXCEEDED);
  }

  private void assertMenuCode(Runnable executable, String expectedCode) {
    assertThatThrownBy(executable::run)
        .isInstanceOf(MenuDomainException.class)
        .extracting("code")
        .isEqualTo(expectedCode);
  }

  private void stubOrderableDish(UUID categoryId, UUID dishId, Dish dish) {
    when(dishRepository.findById(dishId)).thenReturn(Optional.of(dish));
    when(categoryRepository.findById(categoryId))
        .thenReturn(Optional.of(category(categoryId, MenuStatus.ACTIVE)));
  }

  private MenuCategory category(UUID id, MenuStatus status) {
    return new MenuCategory(id, "Drinks", null, 1, status);
  }

  private Dish dish(UUID id, UUID categoryId, MenuStatus status, BigDecimal price) {
    return new Dish(id, categoryId, "Milk Tea", "Fresh", price, status, 1);
  }

  private ToppingGroup group(
      UUID id, UUID dishId, String name, int minSelections, int maxSelections, int sortOrder) {
    return new ToppingGroup(id, dishId, name, minSelections, maxSelections, sortOrder);
  }

  private ToppingOption option(
      UUID id, UUID groupId, String name, BigDecimal price, MenuStatus status, int sortOrder) {
    return new ToppingOption(id, groupId, name, price, status, sortOrder);
  }
}
