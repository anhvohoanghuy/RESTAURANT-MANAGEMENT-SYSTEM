package com.example.feat1.DDD.table_context.infrastructure.repository;

import com.example.feat1.DDD.table_context.domain.model.TableStatus;
import com.example.feat1.DDD.table_context.infrastructure.entity.DiningTableEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiningTableRepository extends JpaRepository<DiningTableEntity, UUID> {
  Optional<DiningTableEntity> findByCode(String code);

  List<DiningTableEntity> findAllByOrderBySortOrderAscCodeAsc();

  List<DiningTableEntity> findByArea_IdInAndStatusOrderBySortOrderAscCodeAsc(
      Collection<UUID> areaIds, TableStatus status);
}
