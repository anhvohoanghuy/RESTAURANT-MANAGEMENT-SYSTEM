package com.example.feat1.DDD.menu_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.PublicMenuResponse;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.RecipeRequest;
import com.example.feat1.DDD.menu_context.domain.model.Dish;
import com.example.feat1.DDD.menu_context.domain.model.MenuCategory;
import com.example.feat1.DDD.menu_context.domain.model.MenuStatus;
import com.example.feat1.DDD.menu_context.domain.model.Recipe;
import com.example.feat1.DDD.menu_context.domain.model.RecipeLine;
import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import com.example.feat1.DDD.menu_context.domain.model.ToppingGroup;
import com.example.feat1.DDD.menu_context.domain.model.ToppingOption;
import com.example.feat1.DDD.menu_context.domain.repository.DishDomainRepository;
import com.example.feat1.DDD.menu_context.domain.repository.MenuCategoryDomainRepository;
import com.example.feat1.DDD.menu_context.domain.repository.RecipeDomainRepository;
import com.example.feat1.DDD.menu_context.domain.repository.ToppingGroupDomainRepository;
import com.example.feat1.DDD.menu_context.domain.repository.ToppingOptionDomainRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MenuCatalogServiceTest {
  private MenuCategoryDomainRepository categoryRepository;
  private DishDomainRepository dishRepository;
  private ToppingGroupDomainRepository toppingGroupRepository;
  private ToppingOptionDomainRepository toppingOptionRepository;
  private RecipeDomainRepository recipeRepository;
  private MenuCatalogService service;

  @BeforeEach
  void setUp() {
    categoryRepository = mock(MenuCategoryDomainRepository.class);
    dishRepository = mock(DishDomainRepository.class);
    toppingGroupRepository = mock(ToppingGroupDomainRepository.class);
    toppingOptionRepository = mock(ToppingOptionDomainRepository.class);
    recipeRepository = mock(RecipeDomainRepository.class);
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
    MenuCategory category = new MenuCategory(categoryId, "Pho", null, 1, MenuStatus.ACTIVE);
    Dish dish =
        new Dish(
            dishId, categoryId, "Pho Bo", "Dish", BigDecimal.valueOf(65000), MenuStatus.ACTIVE, 1);
    ToppingGroup group = new ToppingGroup(groupId, dishId, "Herbs", 0, 2, 1);
    ToppingOption option =
        new ToppingOption(
            UUID.randomUUID(), groupId, "Bean sprouts", BigDecimal.ZERO, MenuStatus.ACTIVE, 1);

    when(categoryRepository.findActiveOrdered()).thenReturn(List.of(category));
    when(dishRepository.findActiveByCategoryIds(List.of(categoryId))).thenReturn(List.of(dish));
    when(toppingGroupRepository.findByDishIds(List.of(dishId))).thenReturn(List.of(group));
    when(toppingOptionRepository.findActiveByToppingGroupIds(List.of(groupId)))
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
    when(dishRepository.findById(dishId))
        .thenReturn(
            Optional.of(
                new Dish(
                    dishId,
                    UUID.randomUUID(),
                    "Milk Tea",
                    null,
                    BigDecimal.valueOf(45000),
                    MenuStatus.ACTIVE,
                    1)));
    when(toppingOptionRepository.findById(optionId))
        .thenReturn(
            Optional.of(
                new ToppingOption(
                    optionId,
                    UUID.randomUUID(),
                    "Black pearl",
                    BigDecimal.valueOf(7000),
                    MenuStatus.ACTIVE,
                    1)));
    when(recipeRepository.save(any(Recipe.class)))
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
}
