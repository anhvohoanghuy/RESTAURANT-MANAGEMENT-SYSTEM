package com.example.feat1.DDD.table_context.infrastructure.repository;

import com.example.feat1.DDD.table_context.infrastructure.entity.TableOccupancyEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TableOccupancyRepository extends JpaRepository<TableOccupancyEntity, UUID> {
  Optional<TableOccupancyEntity> findByTableId(UUID tableId);

  List<TableOccupancyEntity> findByTableIdIn(Collection<UUID> tableIds);
}
