package com.example.feat1.DDD.menu_context.application;

import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.CategoryRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.CategoryResponse;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.DishRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.DishResponse;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.PublicCategory;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.PublicDish;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.PublicMenuResponse;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.PublicToppingGroup;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.PublicToppingOption;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.RecipeRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.RecipeResponse;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.ToppingGroupRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.ToppingGroupResponse;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.ToppingOptionRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.ToppingOptionResponse;
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
import jakarta.persistence.EntityNotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuCatalogService {
  private final MenuCategoryDomainRepository categoryRepository;
  private final DishDomainRepository dishRepository;
  private final ToppingGroupDomainRepository toppingGroupRepository;
  private final ToppingOptionDomainRepository toppingOptionRepository;
  private final RecipeDomainRepository recipeRepository;

  @Transactional
  public CategoryResponse createCategory(CategoryRequest request) {
    MenuCategory category =
        new MenuCategory(
            null,
            request.name(),
            request.description(),
            defaultInt(request.sortOrder()),
            request.status());
    return toCategoryResponse(categoryRepository.save(category));
  }

  @Transactional
  public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
    findCategory(id);
    MenuCategory category =
        new MenuCategory(
            id,
            request.name(),
            request.description(),
            defaultInt(request.sortOrder()),
            request.status());
    return toCategoryResponse(categoryRepository.save(category));
  }

  @Transactional
  public CategoryResponse archiveCategory(UUID id) {
    MenuCategory category = findCategory(id);
    return toCategoryResponse(
        categoryRepository.save(
            new MenuCategory(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getSortOrder(),
                MenuStatus.ARCHIVED)));
  }

  @Transactional
  public DishResponse createDish(DishRequest request) {
    Dish dish =
        new Dish(
            null,
            request.categoryId(),
            request.name(),
            request.description(),
            request.basePrice(),
            request.status(),
            defaultInt(request.sortOrder()));
    findCategory(dish.getCategoryId());
    return toDishResponse(dishRepository.save(dish));
  }

  @Transactional
  public DishResponse updateDish(UUID id, DishRequest request) {
    findDish(id);
    Dish dish =
        new Dish(
            id,
            request.categoryId(),
            request.name(),
            request.description(),
            request.basePrice(),
            request.status(),
            defaultInt(request.sortOrder()));
    findCategory(dish.getCategoryId());
    return toDishResponse(dishRepository.save(dish));
  }

  @Transactional
  public DishResponse archiveDish(UUID id) {
    Dish dish = findDish(id);
    return toDishResponse(
        dishRepository.save(
            new Dish(
                dish.getId(),
                dish.getCategoryId(),
                dish.getName(),
                dish.getDescription(),
                dish.getBasePrice(),
                MenuStatus.ARCHIVED,
                dish.getSortOrder())));
  }

  @Transactional
  public ToppingGroupResponse createToppingGroup(ToppingGroupRequest request) {
    ToppingGroup group =
        new ToppingGroup(
            null,
            request.dishId(),
            request.name(),
            defaultInt(request.minSelections()),
            defaultInt(request.maxSelections()),
            defaultInt(request.sortOrder()));
    findDish(group.getDishId());
    return toGroupResponse(toppingGroupRepository.save(group));
  }

  @Transactional
  public ToppingOptionResponse createToppingOption(ToppingOptionRequest request) {
    ToppingOption option =
        new ToppingOption(
            null,
            request.toppingGroupId(),
            request.name(),
            request.additionalPrice(),
            request.status(),
            defaultInt(request.sortOrder()));
    findGroup(option.getToppingGroupId());
    return toOptionResponse(toppingOptionRepository.save(option));
  }

  @Transactional
  public ToppingOptionResponse archiveToppingOption(UUID id) {
    ToppingOption option = findOption(id);
    return toOptionResponse(
        toppingOptionRepository.save(
            new ToppingOption(
                option.getId(),
                option.getToppingGroupId(),
                option.getName(),
                option.getAdditionalPrice(),
                MenuStatus.ARCHIVED,
                option.getSortOrder())));
  }

  @Transactional
  public RecipeResponse upsertRecipe(RecipeRequest request) {
    List<RecipeLine> lines =
        request.lines() == null
            ? List.of()
            : request.lines().stream()
                .map(
                    line ->
                        new RecipeLine(
                            null,
                            line.ingredientId(),
                            line.ingredient(),
                            line.quantity(),
                            line.unit(),
                            defaultInt(line.sortOrder())))
                .toList();
    Recipe recipe =
        new Recipe(null, request.targetType(), request.targetId(), request.name(), lines);
    assertRecipeTargetExists(recipe.getTargetType(), recipe.getTargetId());

    return toRecipeResponse(recipeRepository.save(recipe));
  }

  @Transactional(readOnly = true)
  public RecipeResponse getRecipe(RecipeTargetType targetType, UUID targetId) {
    return recipeRepository
        .findByTarget(targetType, targetId)
        .map(this::toRecipeResponse)
        .orElseThrow(() -> new EntityNotFoundException("Recipe not found"));
  }

  @Transactional(readOnly = true)
  public PublicMenuResponse getPublicMenu() {
    List<MenuCategory> categories = categoryRepository.findActiveOrdered();
    List<UUID> categoryIds = categories.stream().map(MenuCategory::getId).toList();
    List<Dish> dishes = dishRepository.findActiveByCategoryIds(categoryIds);
    List<UUID> dishIds = dishes.stream().map(Dish::getId).toList();
    List<ToppingGroup> groups = toppingGroupRepository.findByDishIds(dishIds);
    List<UUID> groupIds = groups.stream().map(ToppingGroup::getId).toList();
    List<ToppingOption> options = toppingOptionRepository.findActiveByToppingGroupIds(groupIds);

    Map<UUID, List<ToppingOption>> optionsByGroup =
        options.stream().collect(Collectors.groupingBy(ToppingOption::getToppingGroupId));
    Map<UUID, List<ToppingGroup>> groupsByDish =
        groups.stream().collect(Collectors.groupingBy(ToppingGroup::getDishId));
    Map<UUID, List<Dish>> dishesByCategory =
        dishes.stream().collect(Collectors.groupingBy(Dish::getCategoryId));

    List<PublicCategory> responseCategories =
        categories.stream()
            .map(
                category ->
                    new PublicCategory(
                        category.getId(),
                        category.getName(),
                        category.getDescription(),
                        category.getSortOrder(),
                        dishesByCategory.getOrDefault(category.getId(), List.of()).stream()
                            .sorted(bySortThenName(Dish::getSortOrder, Dish::getName))
                            .map(
                                dish ->
                                    new PublicDish(
                                        dish.getId(),
                                        dish.getName(),
                                        dish.getDescription(),
                                        dish.getBasePrice(),
                                        dish.getSortOrder(),
                                        groupsByDish.getOrDefault(dish.getId(), List.of()).stream()
                                            .sorted(
                                                bySortThenName(
                                                    ToppingGroup::getSortOrder,
                                                    ToppingGroup::getName))
                                            .map(
                                                group ->
                                                    new PublicToppingGroup(
                                                        group.getId(),
                                                        group.getName(),
                                                        group.getMinSelections(),
                                                        group.getMaxSelections(),
                                                        group.getSortOrder(),
                                                        optionsByGroup
                                                            .getOrDefault(group.getId(), List.of())
                                                            .stream()
                                                            .sorted(
                                                                bySortThenName(
                                                                    ToppingOption::getSortOrder,
                                                                    ToppingOption::getName))
                                                            .map(
                                                                option ->
                                                                    new PublicToppingOption(
                                                                        option.getId(),
                                                                        option.getName(),
                                                                        option.getAdditionalPrice(),
                                                                        option.getSortOrder()))
                                                            .toList()))
                                            .toList()))
                            .toList()))
            .toList();
    return new PublicMenuResponse(responseCategories);
  }

  private MenuCategory findCategory(UUID id) {
    return categoryRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Category not found"));
  }

  private Dish findDish(UUID id) {
    return dishRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Dish not found"));
  }

  private ToppingGroup findGroup(UUID id) {
    return toppingGroupRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Topping group not found"));
  }

  private ToppingOption findOption(UUID id) {
    return toppingOptionRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Topping option not found"));
  }

  private void assertRecipeTargetExists(RecipeTargetType targetType, UUID targetId) {
    if (targetType == RecipeTargetType.DISH) {
      findDish(targetId);
    } else if (targetType == RecipeTargetType.TOPPING_OPTION) {
      findOption(targetId);
    }
  }

  private CategoryResponse toCategoryResponse(MenuCategory entity) {
    return new CategoryResponse(
        entity.getId(),
        entity.getName(),
        entity.getDescription(),
        entity.getSortOrder(),
        entity.getStatus());
  }

  private DishResponse toDishResponse(Dish entity) {
    return new DishResponse(
        entity.getId(),
        entity.getCategoryId(),
        entity.getName(),
        entity.getDescription(),
        entity.getBasePrice(),
        entity.getStatus(),
        entity.getSortOrder());
  }

  private ToppingGroupResponse toGroupResponse(ToppingGroup entity) {
    return new ToppingGroupResponse(
        entity.getId(),
        entity.getDishId(),
        entity.getName(),
        entity.getMinSelections(),
        entity.getMaxSelections(),
        entity.getSortOrder());
  }

  private ToppingOptionResponse toOptionResponse(ToppingOption entity) {
    return new ToppingOptionResponse(
        entity.getId(),
        entity.getToppingGroupId(),
        entity.getName(),
        entity.getAdditionalPrice(),
        entity.getStatus(),
        entity.getSortOrder());
  }

  private RecipeResponse toRecipeResponse(Recipe entity) {
    return new RecipeResponse(
        entity.getId(),
        entity.getTargetType(),
        entity.getTargetId(),
        entity.getName(),
        entity.getLines().stream()
            .sorted(Comparator.comparingInt(RecipeLine::getSortOrder))
            .map(
                line ->
                    new RecipeResponse.Line(
                        line.getId(),
                        line.getIngredientId(),
                        line.getIngredient(),
                        line.getQuantity(),
                        line.getUnit(),
                        line.getSortOrder()))
            .toList());
  }

  private int defaultInt(Integer value) {
    return value == null ? 0 : value;
  }

  private static <T> Comparator<T> bySortThenName(
      java.util.function.ToIntFunction<T> sort, java.util.function.Function<T, String> name) {
    return Comparator.comparingInt(sort)
        .thenComparing(name, Comparator.nullsLast(String::compareTo));
  }
}
