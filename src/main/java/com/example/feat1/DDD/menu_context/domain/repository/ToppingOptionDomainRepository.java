package com.example.feat1.DDD.menu_context.domain.repository;

import com.example.feat1.DDD.menu_context.domain.model.ToppingOption;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ToppingOptionDomainRepository {
  ToppingOption save(ToppingOption toppingOption);

  Optional<ToppingOption> findById(UUID id);

  List<ToppingOption> findActiveByToppingGroupIds(Collection<UUID> toppingGroupIds);
}
