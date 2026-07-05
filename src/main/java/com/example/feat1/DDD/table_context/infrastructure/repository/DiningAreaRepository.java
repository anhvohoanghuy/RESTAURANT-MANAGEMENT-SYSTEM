package com.example.feat1.DDD.table_context.infrastructure.repository;

import com.example.feat1.DDD.table_context.domain.model.TableStatus;
import com.example.feat1.DDD.table_context.infrastructure.entity.DiningAreaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiningAreaRepository extends JpaRepository<DiningAreaEntity, UUID> {
  Optional<DiningAreaEntity> findByName(String name);

  List<DiningAreaEntity> findByStatusOrderBySortOrderAscNameAsc(TableStatus status);

  List<DiningAreaEntity> findAllByOrderBySortOrderAscNameAsc();
}
