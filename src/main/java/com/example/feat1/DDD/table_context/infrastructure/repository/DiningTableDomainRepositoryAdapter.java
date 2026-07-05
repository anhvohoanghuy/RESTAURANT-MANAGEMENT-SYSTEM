package com.example.feat1.DDD.table_context.infrastructure.repository;

import com.example.feat1.DDD.table_context.domain.model.DiningTable;
import com.example.feat1.DDD.table_context.domain.model.TableStatus;
import com.example.feat1.DDD.table_context.domain.repository.DiningTableDomainRepository;
import com.example.feat1.DDD.table_context.infrastructure.entity.DiningTableEntity;
import com.example.feat1.DDD.table_context.infrastructure.mapper.TableCatalogMapper;
import jakarta.persistence.EntityNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DiningTableDomainRepositoryAdapter implements DiningTableDomainRepository {
  private final DiningTableRepository jpaRepository;
  private final DiningAreaRepository areaRepository;

  @Override
  public DiningTable save(DiningTable table) {
    DiningTableEntity entity =
        table.getId() == null
            ? new DiningTableEntity()
            : jpaRepository.findById(table.getId()).orElseGet(DiningTableEntity::new);
    entity.setId(table.getId());
    entity.setArea(
        areaRepository
            .findById(table.getAreaId())
            .orElseThrow(() -> new EntityNotFoundException("Dining area not found")));
    entity.setCode(table.getCode());
    entity.setName(table.getName());
    entity.setCapacity(table.getCapacity());
    entity.setSortOrder(table.getSortOrder());
    entity.setStatus(table.getStatus());
    return TableCatalogMapper.toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<DiningTable> findById(UUID id) {
    return jpaRepository.findById(id).map(TableCatalogMapper::toDomain);
  }

  @Override
  public Optional<DiningTable> findByCode(String code) {
    return jpaRepository.findByCode(code).map(TableCatalogMapper::toDomain);
  }

  @Override
  public List<DiningTable> findAllOrdered() {
    return jpaRepository.findAllByOrderBySortOrderAscCodeAsc().stream()
        .map(TableCatalogMapper::toDomain)
        .toList();
  }

  @Override
  public List<DiningTable> findActiveByAreaIds(Collection<UUID> areaIds) {
    if (areaIds.isEmpty()) {
      return List.of();
    }
    return jpaRepository
        .findByArea_IdInAndStatusOrderBySortOrderAscCodeAsc(areaIds, TableStatus.ACTIVE)
        .stream()
        .map(TableCatalogMapper::toDomain)
        .toList();
  }
}
