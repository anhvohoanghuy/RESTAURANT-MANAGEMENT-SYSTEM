package com.example.feat1.DDD.order_context.infrastructure.adapter;

import com.example.feat1.DDD.order_context.domain.port.TableValidationPort;
import com.example.feat1.DDD.order_context.domain.snapshot.OrderTableSnapshot;
import com.example.feat1.DDD.table_context.application.TableCatalogService;
import com.example.feat1.DDD.table_context.domain.model.TableDomainException;
import com.example.feat1.common.exception.AppException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TableValidationAdapter implements TableValidationPort {
  private final TableCatalogService tableCatalogService;

  @Override
  public OrderTableSnapshot validate(UUID tableId) {
    try {
      var table = tableCatalogService.validateOrderableTable(tableId);
      return new OrderTableSnapshot(
          table.tableId(), table.code(), table.name(), table.areaId(), table.areaName());
    } catch (TableDomainException exception) {
      throw new AppException(exception.getCode(), exception.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }
}
