package com.example.feat1.DDD.table_context.infrastructure.adapter;

import com.example.feat1.DDD.order_context.domain.port.TableSessionValidationPort;
import com.example.feat1.DDD.order_context.domain.snapshot.OrderTableSessionSnapshot;
import com.example.feat1.DDD.table_context.application.TableOperationService;
import com.example.feat1.DDD.table_context.domain.model.TableDomainException;
import com.example.feat1.common.exception.AppException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TableSessionValidationAdapter implements TableSessionValidationPort {
  private final TableOperationService tableOperationService;

  @Override
  public OrderTableSessionSnapshot validateOpenSession(UUID tableSessionId, UUID tableId) {
    if (tableSessionId == null) {
      return null;
    }
    try {
      var session = tableOperationService.validateOpenSession(tableSessionId, tableId);
      return new OrderTableSessionSnapshot(session.sessionId(), session.tableId());
    } catch (TableDomainException exception) {
      throw new AppException(exception.getCode(), exception.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }
}
