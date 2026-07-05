package com.example.feat1.DDD.table_context.domain.repository;

import com.example.feat1.DDD.table_context.domain.model.DiningTable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiningTableDomainRepository {
  DiningTable save(DiningTable table);

  Optional<DiningTable> findById(UUID id);

  Optional<DiningTable> findByCode(String code);

  List<DiningTable> findAllOrdered();

  List<DiningTable> findActiveByAreaIds(Collection<UUID> areaIds);
}
