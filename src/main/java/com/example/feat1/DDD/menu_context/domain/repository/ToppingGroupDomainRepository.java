package com.example.feat1.DDD.menu_context.domain.repository;

import com.example.feat1.DDD.menu_context.domain.model.ToppingGroup;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ToppingGroupDomainRepository {
  ToppingGroup save(ToppingGroup toppingGroup);

  Optional<ToppingGroup> findById(UUID id);

  List<ToppingGroup> findByDishIds(Collection<UUID> dishIds);
}
