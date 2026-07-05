package com.example.feat1.DDD.table_context.infrastructure.mapper;

import com.example.feat1.DDD.table_context.domain.model.DiningArea;
import com.example.feat1.DDD.table_context.domain.model.DiningTable;
import com.example.feat1.DDD.table_context.infrastructure.entity.DiningAreaEntity;
import com.example.feat1.DDD.table_context.infrastructure.entity.DiningTableEntity;

public final class TableCatalogMapper {
  private TableCatalogMapper() {}

  public static DiningArea toDomain(DiningAreaEntity entity) {
    return new DiningArea(
        entity.getId(), entity.getName(), entity.getSortOrder(), entity.getStatus());
  }

  public static DiningTable toDomain(DiningTableEntity entity) {
    return new DiningTable(
        entity.getId(),
        entity.getArea().getId(),
        entity.getCode(),
        entity.getName(),
        entity.getCapacity(),
        entity.getSortOrder(),
        entity.getStatus());
  }
}
