package com.example.feat1.DDD.menu_context.infrastructure.repository;

import com.example.feat1.DDD.menu_context.domain.model.MenuCategory;
import com.example.feat1.DDD.menu_context.domain.model.MenuStatus;
import com.example.feat1.DDD.menu_context.domain.repository.MenuCategoryDomainRepository;
import com.example.feat1.DDD.menu_context.infrastructure.entity.MenuCategoryEntity;
import com.example.feat1.DDD.menu_context.infrastructure.mapper.MenuCatalogMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MenuCategoryDomainRepositoryAdapter implements MenuCategoryDomainRepository {
  private final MenuCategoryRepository jpaRepository;

  @Override
  public MenuCategory save(MenuCategory category) {
    MenuCategoryEntity entity =
        category.getId() == null
            ? new MenuCategoryEntity()
            : jpaRepository.findById(category.getId()).orElseGet(MenuCategoryEntity::new);
    entity.setId(category.getId());
    entity.setName(category.getName());
    entity.setDescription(category.getDescription());
    entity.setSortOrder(category.getSortOrder());
    entity.setStatus(category.getStatus());
    return MenuCatalogMapper.toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<MenuCategory> findById(UUID id) {
    return jpaRepository.findById(id).map(MenuCatalogMapper::toDomain);
  }

  @Override
  public List<MenuCategory> findActiveOrdered() {
    return jpaRepository.findByStatusOrderBySortOrderAscNameAsc(MenuStatus.ACTIVE).stream()
        .map(MenuCatalogMapper::toDomain)
        .toList();
  }
}
