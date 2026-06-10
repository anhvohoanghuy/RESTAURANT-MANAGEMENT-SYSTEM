package com.example.feat1.DDD.menu_context.domain.repository;

import com.example.feat1.DDD.menu_context.domain.model.MenuCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuCategoryDomainRepository {
  MenuCategory save(MenuCategory category);

  Optional<MenuCategory> findById(UUID id);

  List<MenuCategory> findActiveOrdered();
}
