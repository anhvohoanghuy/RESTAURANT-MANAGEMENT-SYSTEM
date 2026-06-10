package com.example.feat1.DDD.menu_context.infrastructure.repository;

import com.example.feat1.DDD.menu_context.domain.model.MenuStatus;
import com.example.feat1.DDD.menu_context.domain.model.ToppingOption;
import com.example.feat1.DDD.menu_context.domain.repository.ToppingOptionDomainRepository;
import com.example.feat1.DDD.menu_context.infrastructure.entity.ToppingOptionEntity;
import com.example.feat1.DDD.menu_context.infrastructure.mapper.MenuCatalogMapper;
import jakarta.persistence.EntityNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ToppingOptionDomainRepositoryAdapter implements ToppingOptionDomainRepository {
  private final ToppingOptionRepository jpaRepository;
  private final ToppingGroupRepository toppingGroupRepository;

  @Override
  public ToppingOption save(ToppingOption toppingOption) {
    ToppingOptionEntity entity =
        toppingOption.getId() == null
            ? new ToppingOptionEntity()
            : jpaRepository.findById(toppingOption.getId()).orElseGet(ToppingOptionEntity::new);
    entity.setId(toppingOption.getId());
    entity.setToppingGroup(
        toppingGroupRepository
            .findById(toppingOption.getToppingGroupId())
            .orElseThrow(() -> new EntityNotFoundException("Topping group not found")));
    entity.setName(toppingOption.getName());
    entity.setAdditionalPrice(toppingOption.getAdditionalPrice());
    entity.setStatus(toppingOption.getStatus());
    entity.setSortOrder(toppingOption.getSortOrder());
    return MenuCatalogMapper.toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<ToppingOption> findById(UUID id) {
    return jpaRepository.findById(id).map(MenuCatalogMapper::toDomain);
  }

  @Override
  public List<ToppingOption> findActiveByToppingGroupIds(Collection<UUID> toppingGroupIds) {
    if (toppingGroupIds.isEmpty()) {
      return List.of();
    }
    return jpaRepository
        .findByToppingGroup_IdInAndStatusOrderBySortOrderAscNameAsc(
            toppingGroupIds, MenuStatus.ACTIVE)
        .stream()
        .map(MenuCatalogMapper::toDomain)
        .toList();
  }
}
