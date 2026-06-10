package com.example.feat1.DDD.menu_context.infrastructure.repository;

import com.example.feat1.DDD.menu_context.domain.model.MenuStatus;
import com.example.feat1.DDD.menu_context.infrastructure.entity.ToppingOptionEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToppingOptionRepository extends JpaRepository<ToppingOptionEntity, UUID> {
  List<ToppingOptionEntity> findByToppingGroup_IdInAndStatusOrderBySortOrderAscNameAsc(
      Collection<UUID> toppingGroupIds, MenuStatus status);
}
