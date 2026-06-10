package com.example.feat1.DDD.menu_context.infrastructure.repository;

import com.example.feat1.DDD.menu_context.infrastructure.entity.ToppingGroupEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToppingGroupRepository extends JpaRepository<ToppingGroupEntity, UUID> {
  List<ToppingGroupEntity> findByDish_IdInOrderBySortOrderAscNameAsc(Collection<UUID> dishIds);
}
