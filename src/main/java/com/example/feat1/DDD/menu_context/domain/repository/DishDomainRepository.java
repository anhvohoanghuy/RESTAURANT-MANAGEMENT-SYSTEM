package com.example.feat1.DDD.menu_context.domain.repository;

import com.example.feat1.DDD.menu_context.domain.model.Dish;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DishDomainRepository {
  Dish save(Dish dish);

  Optional<Dish> findById(UUID id);

  List<Dish> findActiveByCategoryIds(Collection<UUID> categoryIds);
}
