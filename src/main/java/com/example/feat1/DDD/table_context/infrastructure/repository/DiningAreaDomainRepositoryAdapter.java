package com.example.feat1.DDD.table_context.infrastructure.repository;

import com.example.feat1.DDD.table_context.domain.model.DiningArea;
import com.example.feat1.DDD.table_context.domain.model.TableStatus;
import com.example.feat1.DDD.table_context.domain.repository.DiningAreaDomainRepository;
import com.example.feat1.DDD.table_context.infrastructure.entity.DiningAreaEntity;
import com.example.feat1.DDD.table_context.infrastructure.mapper.TableCatalogMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DiningAreaDomainRepositoryAdapter implements DiningAreaDomainRepository {
  private final DiningAreaRepository jpaRepository;

  @Override
  public DiningArea save(DiningArea area) {
    DiningAreaEntity entity =
        area.getId() == null
            ? new DiningAreaEntity()
            : jpaRepository.findById(area.getId()).orElseGet(DiningAreaEntity::new);
    entity.setId(area.getId());
    entity.setName(area.getName());
    entity.setSortOrder(area.getSortOrder());
    entity.setStatus(area.getStatus());
    return TableCatalogMapper.toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<DiningArea> findById(UUID id) {
    return jpaRepository.findById(id).map(TableCatalogMapper::toDomain);
  }

  @Override
  public Optional<DiningArea> findByName(String name) {
    return jpaRepository.findByName(name).map(TableCatalogMapper::toDomain);
  }

  @Override
  public List<DiningArea> findAllOrdered() {
    return jpaRepository.findAllByOrderBySortOrderAscNameAsc().stream()
        .map(TableCatalogMapper::toDomain)
        .toList();
  }

  @Override
  public List<DiningArea> findActiveOrdered() {
    return jpaRepository.findByStatusOrderBySortOrderAscNameAsc(TableStatus.ACTIVE).stream()
        .map(TableCatalogMapper::toDomain)
        .toList();
  }
}
