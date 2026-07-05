package com.example.feat1.DDD.table_context.domain.repository;

import com.example.feat1.DDD.table_context.domain.model.DiningArea;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiningAreaDomainRepository {
  DiningArea save(DiningArea area);

  Optional<DiningArea> findById(UUID id);

  Optional<DiningArea> findByName(String name);

  List<DiningArea> findAllOrdered();

  List<DiningArea> findActiveOrdered();
}
