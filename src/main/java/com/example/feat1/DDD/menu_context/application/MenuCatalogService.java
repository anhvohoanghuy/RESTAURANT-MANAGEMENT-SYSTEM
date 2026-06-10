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
import com.example.feat1.DDD.menu_context.infrastructure.entity.DishEntity;
import com.example.feat1.DDD.menu_context.infrastructure.entity.MenuCategoryEntity;
import com.example.feat1.DDD.menu_context.infrastructure.entity.RecipeEntity;
import com.example.feat1.DDD.menu_context.infrastructure.entity.RecipeLineEntity;
import com.example.feat1.DDD.menu_context.infrastructure.entity.ToppingGroupEntity;
import com.example.feat1.DDD.menu_context.infrastructure.entity.ToppingOptionEntity;
import com.example.feat1.DDD.menu_context.infrastructure.repository.DishRepository;
import com.example.feat1.DDD.menu_context.infrastructure.repository.MenuCategoryRepository;
import com.example.feat1.DDD.menu_context.infrastructure.repository.RecipeRepository;
import com.example.feat1.DDD.menu_context.infrastructure.repository.ToppingGroupRepository;
import com.example.feat1.DDD.menu_context.infrastructure.repository.ToppingOptionRepository;
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
  private final MenuCategoryRepository categoryRepository;
  private final DishRepository dishRepository;
  private final ToppingGroupRepository toppingGroupRepository;
  private final ToppingOptionRepository toppingOptionRepository;
  private final RecipeRepository recipeRepository;

  @Transactional
  public CategoryResponse createCategory(CategoryRequest request) {
    MenuCategory category =
        new MenuCategory(
            null,
            request.name(),
            request.description(),
            defaultInt(request.sortOrder()),
            request.status());
    MenuCategoryEntity entity = new MenuCategoryEntity();
    entity.setName(category.getName());
    entity.setDescription(category.getDescription());
    entity.setSortOrder(category.getSortOrder());
    entity.setStatus(category.getStatus());
    return toCategoryResponse(categoryRepository.save(entity));
  }

  @Transactional
  public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
    MenuCategoryEntity entity = findCategory(id);
    MenuCategory category =
        new MenuCategory(
            id,
            request.name(),
            request.description(),
            defaultInt(request.sortOrder()),
            request.status());
    entity.setName(category.getName());
    entity.setDescription(category.getDescription());
    entity.setSortOrder(category.getSortOrder());
    entity.setStatus(category.getStatus());
    return toCategoryResponse(categoryRepository.save(entity));
  }

  @Transactional
  public CategoryResponse archiveCategory(UUID id) {
    MenuCategoryEntity entity = findCategory(id);
    entity.setStatus(MenuStatus.ARCHIVED);
    return toCategoryResponse(categoryRepository.save(entity));
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
    DishEntity entity = new DishEntity();
    entity.setCategory(findCategory(dish.getCategoryId()));
    entity.setName(dish.getName());
    entity.setDescription(dish.getDescription());
    entity.setBasePrice(dish.getBasePrice());
    entity.setStatus(dish.getStatus());
    entity.setSortOrder(dish.getSortOrder());
    return toDishResponse(dishRepository.save(entity));
  }

  @Transactional
  public DishResponse updateDish(UUID id, DishRequest request) {
    DishEntity entity = findDish(id);
    Dish dish =
        new Dish(
            id,
            request.categoryId(),
            request.name(),
            request.description(),
            request.basePrice(),
            request.status(),
            defaultInt(request.sortOrder()));
    entity.setCategory(findCategory(dish.getCategoryId()));
    entity.setName(dish.getName());
    entity.setDescription(dish.getDescription());
    entity.setBasePrice(dish.getBasePrice());
    entity.setStatus(dish.getStatus());
    entity.setSortOrder(dish.getSortOrder());
    return toDishResponse(dishRepository.save(entity));
  }

  @Transactional
  public DishResponse archiveDish(UUID id) {
    DishEntity entity = findDish(id);
    entity.setStatus(MenuStatus.ARCHIVED);
    return toDishResponse(dishRepository.save(entity));
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
    ToppingGroupEntity entity = new ToppingGroupEntity();
    entity.setDish(findDish(group.getDishId()));
    entity.setName(group.getName());
    entity.setMinSelections(group.getMinSelections());
    entity.setMaxSelections(group.getMaxSelections());
    entity.setSortOrder(group.getSortOrder());
    return toGroupResponse(toppingGroupRepository.save(entity));
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
    ToppingOptionEntity entity = new ToppingOptionEntity();
    entity.setToppingGroup(findGroup(option.getToppingGroupId()));
    entity.setName(option.getName());
    entity.setAdditionalPrice(option.getAdditionalPrice());
    entity.setStatus(option.getStatus());
    entity.setSortOrder(option.getSortOrder());
    return toOptionResponse(toppingOptionRepository.save(entity));
  }

  @Transactional
  public ToppingOptionResponse archiveToppingOption(UUID id) {
    ToppingOptionEntity entity = findOption(id);
    entity.setStatus(MenuStatus.ARCHIVED);
    return toOptionResponse(toppingOptionRepository.save(entity));
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
                            line.ingredient(),
                            line.quantity(),
                            line.unit(),
                            defaultInt(line.sortOrder())))
                .toList();
    Recipe recipe =
        new Recipe(null, request.targetType(), request.targetId(), request.name(), lines);
    assertRecipeTargetExists(recipe.getTargetType(), recipe.getTargetId());

    RecipeEntity entity =
        recipeRepository
            .findByTargetTypeAndTargetId(recipe.getTargetType(), recipe.getTargetId())
            .orElseGet(RecipeEntity::new);
    entity.setTargetType(recipe.getTargetType());
    entity.setTargetId(recipe.getTargetId());
    entity.setName(recipe.getName());
    entity.getLines().clear();
    recipe
        .getLines()
        .forEach(
            line -> {
              RecipeLineEntity lineEntity = new RecipeLineEntity();
              lineEntity.setRecipe(entity);
              lineEntity.setIngredient(line.getIngredient());
              lineEntity.setQuantity(line.getQuantity());
              lineEntity.setUnit(line.getUnit());
              lineEntity.setSortOrder(line.getSortOrder());
              entity.getLines().add(lineEntity);
            });
    return toRecipeResponse(recipeRepository.save(entity));
  }

  @Transactional(readOnly = true)
  public RecipeResponse getRecipe(RecipeTargetType targetType, UUID targetId) {
    return recipeRepository
        .findByTargetTypeAndTargetId(targetType, targetId)
        .map(this::toRecipeResponse)
        .orElseThrow(() -> new EntityNotFoundException("Recipe not found"));
  }

  @Transactional(readOnly = true)
  public PublicMenuResponse getPublicMenu() {
    List<MenuCategoryEntity> categories =
        categoryRepository.findByStatusOrderBySortOrderAscNameAsc(MenuStatus.ACTIVE);
    List<UUID> categoryIds = categories.stream().map(MenuCategoryEntity::getId).toList();
    List<DishEntity> dishes =
        categoryIds.isEmpty()
            ? List.of()
            : dishRepository.findByCategory_IdInAndStatusOrderBySortOrderAscNameAsc(
                categoryIds, MenuStatus.ACTIVE);
    List<UUID> dishIds = dishes.stream().map(DishEntity::getId).toList();
    List<ToppingGroupEntity> groups =
        dishIds.isEmpty()
            ? List.of()
            : toppingGroupRepository.findByDish_IdInOrderBySortOrderAscNameAsc(dishIds);
    List<UUID> groupIds = groups.stream().map(ToppingGroupEntity::getId).toList();
    List<ToppingOptionEntity> options =
        groupIds.isEmpty()
            ? List.of()
            : toppingOptionRepository.findByToppingGroup_IdInAndStatusOrderBySortOrderAscNameAsc(
                groupIds, MenuStatus.ACTIVE);

    Map<UUID, List<ToppingOptionEntity>> optionsByGroup =
        options.stream().collect(Collectors.groupingBy(option -> option.getToppingGroup().getId()));
    Map<UUID, List<ToppingGroupEntity>> groupsByDish =
        groups.stream().collect(Collectors.groupingBy(group -> group.getDish().getId()));
    Map<UUID, List<DishEntity>> dishesByCategory =
        dishes.stream().collect(Collectors.groupingBy(dish -> dish.getCategory().getId()));

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
                            .sorted(bySortThenName(DishEntity::getSortOrder, DishEntity::getName))
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
                                                    ToppingGroupEntity::getSortOrder,
                                                    ToppingGroupEntity::getName))
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
                                                                    ToppingOptionEntity
                                                                        ::getSortOrder,
                                                                    ToppingOptionEntity::getName))
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

  private MenuCategoryEntity findCategory(UUID id) {
    return categoryRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Category not found"));
  }

  private DishEntity findDish(UUID id) {
    return dishRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Dish not found"));
  }

  private ToppingGroupEntity findGroup(UUID id) {
    return toppingGroupRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Topping group not found"));
  }

  private ToppingOptionEntity findOption(UUID id) {
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

  private CategoryResponse toCategoryResponse(MenuCategoryEntity entity) {
    return new CategoryResponse(
        entity.getId(),
        entity.getName(),
        entity.getDescription(),
        entity.getSortOrder(),
        entity.getStatus());
  }

  private DishResponse toDishResponse(DishEntity entity) {
    return new DishResponse(
        entity.getId(),
        entity.getCategory().getId(),
        entity.getName(),
        entity.getDescription(),
        entity.getBasePrice(),
        entity.getStatus(),
        entity.getSortOrder());
  }

  private ToppingGroupResponse toGroupResponse(ToppingGroupEntity entity) {
    return new ToppingGroupResponse(
        entity.getId(),
        entity.getDish().getId(),
        entity.getName(),
        entity.getMinSelections(),
        entity.getMaxSelections(),
        entity.getSortOrder());
  }

  private ToppingOptionResponse toOptionResponse(ToppingOptionEntity entity) {
    return new ToppingOptionResponse(
        entity.getId(),
        entity.getToppingGroup().getId(),
        entity.getName(),
        entity.getAdditionalPrice(),
        entity.getStatus(),
        entity.getSortOrder());
  }

  private RecipeResponse toRecipeResponse(RecipeEntity entity) {
    return new RecipeResponse(
        entity.getId(),
        entity.getTargetType(),
        entity.getTargetId(),
        entity.getName(),
        entity.getLines().stream()
            .sorted(Comparator.comparingInt(RecipeLineEntity::getSortOrder))
            .map(
                line ->
                    new RecipeResponse.Line(
                        line.getId(),
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
