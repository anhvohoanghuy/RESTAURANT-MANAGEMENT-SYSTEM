package com.example.feat1.DDD.menu_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.PublicMenuResponse;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.RecipeRequest;
import com.example.feat1.DDD.menu_context.domain.model.MenuStatus;
import com.example.feat1.DDD.menu_context.domain.model.RecipeLine;
import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import com.example.feat1.DDD.menu_context.domain.model.ToppingGroup;
import com.example.feat1.DDD.menu_context.infrastructure.entity.DishEntity;
import com.example.feat1.DDD.menu_context.infrastructure.entity.MenuCategoryEntity;
import com.example.feat1.DDD.menu_context.infrastructure.entity.RecipeEntity;
import com.example.feat1.DDD.menu_context.infrastructure.entity.ToppingGroupEntity;
import com.example.feat1.DDD.menu_context.infrastructure.entity.ToppingOptionEntity;
import com.example.feat1.DDD.menu_context.infrastructure.repository.DishRepository;
import com.example.feat1.DDD.menu_context.infrastructure.repository.MenuCategoryRepository;
import com.example.feat1.DDD.menu_context.infrastructure.repository.RecipeRepository;
import com.example.feat1.DDD.menu_context.infrastructure.repository.ToppingGroupRepository;
import com.example.feat1.DDD.menu_context.infrastructure.repository.ToppingOptionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MenuCatalogServiceTest {
  private MenuCategoryRepository categoryRepository;
  private DishRepository dishRepository;
  private ToppingGroupRepository toppingGroupRepository;
  private ToppingOptionRepository toppingOptionRepository;
  private RecipeRepository recipeRepository;
  private MenuCatalogService service;

  @BeforeEach
  void setUp() {
    categoryRepository = mock(MenuCategoryRepository.class);
    dishRepository = mock(DishRepository.class);
    toppingGroupRepository = mock(ToppingGroupRepository.class);
    toppingOptionRepository = mock(ToppingOptionRepository.class);
    recipeRepository = mock(RecipeRepository.class);
    service =
        new MenuCatalogService(
            categoryRepository,
            dishRepository,
            toppingGroupRepository,
            toppingOptionRepository,
            recipeRepository);
  }

  @Test
  void publicMenuOnlyReturnsActiveCatalogTree() {
    UUID categoryId = UUID.randomUUID();
    UUID dishId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    MenuCategoryEntity category = category(categoryId, "Pho", MenuStatus.ACTIVE, 1);
    DishEntity dish = dish(dishId, category, "Pho Bo", MenuStatus.ACTIVE, 1);
    ToppingGroupEntity group = group(groupId, dish, "Herbs", 0, 2, 1);
    ToppingOptionEntity option =
        option(UUID.randomUUID(), group, "Bean sprouts", MenuStatus.ACTIVE, 1);

    when(categoryRepository.findByStatusOrderBySortOrderAscNameAsc(MenuStatus.ACTIVE))
        .thenReturn(List.of(category));
    when(dishRepository.findByCategory_IdInAndStatusOrderBySortOrderAscNameAsc(
            List.of(categoryId), MenuStatus.ACTIVE))
        .thenReturn(List.of(dish));
    when(toppingGroupRepository.findByDish_IdInOrderBySortOrderAscNameAsc(List.of(dishId)))
        .thenReturn(List.of(group));
    when(toppingOptionRepository.findByToppingGroup_IdInAndStatusOrderBySortOrderAscNameAsc(
            List.of(groupId), MenuStatus.ACTIVE))
        .thenReturn(List.of(option));

    PublicMenuResponse menu = service.getPublicMenu();

    assertThat(menu.categories()).hasSize(1);
    assertThat(menu.categories().get(0).name()).isEqualTo("Pho");
    assertThat(menu.categories().get(0).dishes()).extracting("name").containsExactly("Pho Bo");
    assertThat(menu.categories().get(0).dishes().get(0).toppingGroups()).hasSize(1);
    assertThat(menu.categories().get(0).dishes().get(0).toppingGroups().get(0).options())
        .extracting("name")
        .containsExactly("Bean sprouts");
  }

  @Test
  void toppingGroupRejectsInvalidSelectionRange() {
    assertThatThrownBy(
            () -> new ToppingGroup(UUID.randomUUID(), UUID.randomUUID(), "Sauce", 2, 1, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("min <= max");
  }

  @Test
  void recipeLineRequiresIngredientQuantityAndUnit() {
    assertThatThrownBy(() -> new RecipeLine(null, "", BigDecimal.ONE, "g", 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RecipeLine(null, "Sugar", BigDecimal.ZERO, "g", 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RecipeLine(null, "Sugar", BigDecimal.ONE, "", 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void dishAndToppingCanHaveSeparateRecipes() {
    UUID dishId = UUID.randomUUID();
    UUID optionId = UUID.randomUUID();
    when(dishRepository.findById(dishId)).thenReturn(Optional.of(new DishEntity()));
    when(toppingOptionRepository.findById(optionId))
        .thenReturn(Optional.of(new ToppingOptionEntity()));
    when(recipeRepository.findByTargetTypeAndTargetId(any(), any())).thenReturn(Optional.empty());
    when(recipeRepository.save(any(RecipeEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var dishRecipe =
        service.upsertRecipe(
            new RecipeRequest(
                RecipeTargetType.DISH,
                dishId,
                "Milk tea base",
                List.of(new RecipeRequest.Line("Tea", BigDecimal.valueOf(200), "ml", 1))));
    var toppingRecipe =
        service.upsertRecipe(
            new RecipeRequest(
                RecipeTargetType.TOPPING_OPTION,
                optionId,
                "Pearl batch",
                List.of(new RecipeRequest.Line("Tapioca", BigDecimal.valueOf(30), "g", 1))));

    assertThat(dishRecipe.targetType()).isEqualTo(RecipeTargetType.DISH);
    assertThat(toppingRecipe.targetType()).isEqualTo(RecipeTargetType.TOPPING_OPTION);
    assertThat(dishRecipe.lines()).hasSize(1);
    assertThat(toppingRecipe.lines()).hasSize(1);
  }

  private MenuCategoryEntity category(UUID id, String name, MenuStatus status, int sortOrder) {
    MenuCategoryEntity entity = new MenuCategoryEntity();
    entity.setId(id);
    entity.setName(name);
    entity.setStatus(status);
    entity.setSortOrder(sortOrder);
    return entity;
  }

  private DishEntity dish(
      UUID id, MenuCategoryEntity category, String name, MenuStatus status, int sortOrder) {
    DishEntity entity = new DishEntity();
    entity.setId(id);
    entity.setCategory(category);
    entity.setName(name);
    entity.setDescription("Dish");
    entity.setBasePrice(BigDecimal.valueOf(65000));
    entity.setStatus(status);
    entity.setSortOrder(sortOrder);
    return entity;
  }

  private ToppingGroupEntity group(
      UUID id, DishEntity dish, String name, int minSelections, int maxSelections, int sortOrder) {
    ToppingGroupEntity entity = new ToppingGroupEntity();
    entity.setId(id);
    entity.setDish(dish);
    entity.setName(name);
    entity.setMinSelections(minSelections);
    entity.setMaxSelections(maxSelections);
    entity.setSortOrder(sortOrder);
    return entity;
  }

  private ToppingOptionEntity option(
      UUID id, ToppingGroupEntity group, String name, MenuStatus status, int sortOrder) {
    ToppingOptionEntity entity = new ToppingOptionEntity();
    entity.setId(id);
    entity.setToppingGroup(group);
    entity.setName(name);
    entity.setAdditionalPrice(BigDecimal.ZERO);
    entity.setStatus(status);
    entity.setSortOrder(sortOrder);
    return entity;
  }
}
