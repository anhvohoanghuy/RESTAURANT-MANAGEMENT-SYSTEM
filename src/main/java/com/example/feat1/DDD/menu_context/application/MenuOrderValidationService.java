package com.example.feat1.DDD.menu_context.application;

import com.example.feat1.DDD.menu_context.application.dto.MenuSelectionDtos.MenuSelectionQuote;
import com.example.feat1.DDD.menu_context.application.dto.MenuSelectionDtos.MenuSelectionRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuSelectionDtos.SelectedToppingSnapshot;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuOrderValidationService {
  private final MenuCategoryDomainRepository categoryRepository;
  private final DishDomainRepository dishRepository;
  private final ToppingGroupDomainRepository toppingGroupRepository;
  private final ToppingOptionDomainRepository toppingOptionRepository;

  @Transactional(readOnly = true)
  public MenuSelectionQuote validateAndQuote(MenuSelectionRequest request) {
    Dish dish = requireOrderableDish(request == null ? null : request.dishId());
    List<ToppingGroup> groups = toppingGroupRepository.findByDishIds(List.of(dish.getId()));
    Map<UUID, ToppingGroup> groupsById =
        groups.stream().collect(Collectors.toMap(ToppingGroup::getId, Function.identity()));

    List<ToppingOption> selectedOptions =
        selectedOptionIds(request).stream().map(this::requireOrderableOption).toList();

    selectedOptions.forEach(
        option -> {
          if (!groupsById.containsKey(option.getToppingGroupId())) {
            throw MenuDomainException.toppingNotInDish();
          }
        });

    Map<UUID, List<ToppingOption>> selectedByGroup =
        selectedOptions.stream().collect(Collectors.groupingBy(ToppingOption::getToppingGroupId));
    groups.forEach(
        group -> enforceGroupRange(group, selectedByGroup.getOrDefault(group.getId(), List.of())));

    List<SelectedToppingSnapshot> selectedToppings = buildSnapshots(groups, selectedByGroup);
    BigDecimal toppingsTotal =
        selectedToppings.stream()
            .map(SelectedToppingSnapshot::additionalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new MenuSelectionQuote(
        dish.getId(),
        dish.getName(),
        dish.getBasePrice(),
        selectedToppings,
        toppingsTotal,
        dish.getBasePrice().add(toppingsTotal));
  }

  private Dish requireOrderableDish(UUID dishId) {
    if (dishId == null) {
      throw MenuDomainException.dishNotOrderable();
    }

    Dish dish = dishRepository.findById(dishId).orElseThrow(MenuDomainException::dishNotOrderable);
    if (dish.getStatus() != MenuStatus.ACTIVE) {
      throw MenuDomainException.dishNotOrderable();
    }

    MenuCategory category =
        categoryRepository
            .findById(dish.getCategoryId())
            .orElseThrow(MenuDomainException::dishNotOrderable);
    if (category.getStatus() != MenuStatus.ACTIVE) {
      throw MenuDomainException.dishNotOrderable();
    }
    return dish;
  }

  private ToppingOption requireOrderableOption(UUID optionId) {
    if (optionId == null) {
      throw MenuDomainException.toppingNotOrderable();
    }
    ToppingOption option =
        toppingOptionRepository
            .findById(optionId)
            .orElseThrow(MenuDomainException::toppingNotOrderable);
    if (option.getStatus() != MenuStatus.ACTIVE) {
      throw MenuDomainException.toppingNotOrderable();
    }
    return option;
  }

  private List<UUID> selectedOptionIds(MenuSelectionRequest request) {
    if (request == null || request.toppingOptionIds() == null) {
      return List.of();
    }
    return new ArrayList<>(new LinkedHashSet<>(request.toppingOptionIds()));
  }

  private void enforceGroupRange(ToppingGroup group, List<ToppingOption> selectedOptions) {
    int selectedCount = selectedOptions.size();
    if (selectedCount < group.getMinSelections()) {
      throw MenuDomainException.toppingGroupRequired(group.getName());
    }
    if (selectedCount > group.getMaxSelections()) {
      throw MenuDomainException.toppingGroupLimitExceeded(group.getName());
    }
  }

  private List<SelectedToppingSnapshot> buildSnapshots(
      List<ToppingGroup> groups, Map<UUID, List<ToppingOption>> selectedByGroup) {
    List<SelectedToppingSnapshot> snapshots = new ArrayList<>();
    for (ToppingGroup group : groups) {
      selectedByGroup.getOrDefault(group.getId(), List.of()).stream()
          .sorted(bySortThenName())
          .map(
              option ->
                  new SelectedToppingSnapshot(
                      group.getId(),
                      group.getName(),
                      option.getId(),
                      option.getName(),
                      option.getAdditionalPrice()))
          .forEach(snapshots::add);
    }
    return List.copyOf(snapshots);
  }

  private Comparator<ToppingOption> bySortThenName() {
    return Comparator.comparingInt(ToppingOption::getSortOrder)
        .thenComparing(ToppingOption::getName, Comparator.nullsLast(String::compareTo));
  }
}
