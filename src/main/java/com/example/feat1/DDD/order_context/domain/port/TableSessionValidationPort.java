package com.example.feat1.DDD.order_context.domain.port;

import com.example.feat1.DDD.order_context.domain.snapshot.OrderTableSessionSnapshot;
import java.util.UUID;

public interface TableSessionValidationPort {
  OrderTableSessionSnapshot validateOpenSession(UUID tableSessionId, UUID tableId);
}
