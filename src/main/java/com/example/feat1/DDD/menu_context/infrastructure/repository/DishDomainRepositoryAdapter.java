package com.example.feat1.DDD.menu_context.infrastructure.repository;

import com.example.feat1.DDD.menu_context.domain.model.Dish;
import com.example.feat1.DDD.menu_context.domain.model.MenuStatus;
import com.example.feat1.DDD.menu_context.domain.repository.DishDomainRepository;
import com.example.feat1.DDD.menu_context.infrastructure.entity.DishEntity;
import com.example.feat1.DDD.menu_context.infrastructure.mapper.MenuCatalogMapper;
import jakarta.persistence.EntityNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DishDomainRepositoryAdapter implements DishDomainRepository {
  private final DishRepository jpaRepository;
  private final MenuCategoryRepository categoryRepository;

  @Override
  public Dish save(Dish dish) {
    DishEntity entity =
        dish.getId() == null
            ? new DishEntity()
            : jpaRepository.findById(dish.getId()).orElseGet(DishEntity::new);
    entity.setId(dish.getId());
    entity.setCategory(
        categoryRepository
            .findById(dish.getCategoryId())
            .orElseThrow(() -> new EntityNotFoundException("Category not found")));
    entity.setName(dish.getName());
    entity.setDescription(dish.getDescription());
    entity.setBasePrice(dish.getBasePrice());
    entity.setStatus(dish.getStatus());
    entity.setSortOrder(dish.getSortOrder());
    return MenuCatalogMapper.toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<Dish> findById(UUID id) {
    return jpaRepository.findById(id).map(MenuCatalogMapper::toDomain);
  }

  @Override
  public List<Dish> findActiveByCategoryIds(Collection<UUID> categoryIds) {
    if (categoryIds.isEmpty()) {
      return List.of();
    }
    return jpaRepository
        .findByCategory_IdInAndStatusOrderBySortOrderAscNameAsc(categoryIds, MenuStatus.ACTIVE)
        .stream()
        .map(MenuCatalogMapper::toDomain)
        .toList();
  }
}
