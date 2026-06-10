package com.example.feat1.DDD.menu_context.infrastructure.repository;

import com.example.feat1.DDD.menu_context.domain.model.ToppingGroup;
import com.example.feat1.DDD.menu_context.domain.repository.ToppingGroupDomainRepository;
import com.example.feat1.DDD.menu_context.infrastructure.entity.ToppingGroupEntity;
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
public class ToppingGroupDomainRepositoryAdapter implements ToppingGroupDomainRepository {
  private final ToppingGroupRepository jpaRepository;
  private final DishRepository dishRepository;

  @Override
  public ToppingGroup save(ToppingGroup toppingGroup) {
    ToppingGroupEntity entity =
        toppingGroup.getId() == null
            ? new ToppingGroupEntity()
            : jpaRepository.findById(toppingGroup.getId()).orElseGet(ToppingGroupEntity::new);
    entity.setId(toppingGroup.getId());
    entity.setDish(
        dishRepository
            .findById(toppingGroup.getDishId())
            .orElseThrow(() -> new EntityNotFoundException("Dish not found")));
    entity.setName(toppingGroup.getName());
    entity.setMinSelections(toppingGroup.getMinSelections());
    entity.setMaxSelections(toppingGroup.getMaxSelections());
    entity.setSortOrder(toppingGroup.getSortOrder());
    return MenuCatalogMapper.toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<ToppingGroup> findById(UUID id) {
    return jpaRepository.findById(id).map(MenuCatalogMapper::toDomain);
  }

  @Override
  public List<ToppingGroup> findByDishIds(Collection<UUID> dishIds) {
    if (dishIds.isEmpty()) {
      return List.of();
    }
    return jpaRepository.findByDish_IdInOrderBySortOrderAscNameAsc(dishIds).stream()
        .map(MenuCatalogMapper::toDomain)
        .toList();
  }
}
