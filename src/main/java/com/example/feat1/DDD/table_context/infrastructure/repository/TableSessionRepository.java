package com.example.feat1.DDD.table_context.infrastructure.repository;

import com.example.feat1.DDD.table_context.domain.model.TableSessionStatus;
import com.example.feat1.DDD.table_context.infrastructure.entity.TableSessionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TableSessionRepository extends JpaRepository<TableSessionEntity, UUID> {
  boolean existsByTableIdAndStatus(UUID tableId, TableSessionStatus status);

  Optional<TableSessionEntity> findByTableIdAndStatus(UUID tableId, TableSessionStatus status);

  List<TableSessionEntity> findByTableIdInAndStatus(
      Collection<UUID> tableIds, TableSessionStatus status);
}
