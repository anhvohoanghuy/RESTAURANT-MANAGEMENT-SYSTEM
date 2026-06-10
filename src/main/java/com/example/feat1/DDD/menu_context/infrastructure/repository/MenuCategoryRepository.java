package com.example.feat1.DDD.menu_context.infrastructure.repository;

import com.example.feat1.DDD.menu_context.domain.model.MenuStatus;
import com.example.feat1.DDD.menu_context.infrastructure.entity.MenuCategoryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuCategoryRepository extends JpaRepository<MenuCategoryEntity, UUID> {
  List<MenuCategoryEntity> findByStatusOrderBySortOrderAscNameAsc(MenuStatus status);
}
