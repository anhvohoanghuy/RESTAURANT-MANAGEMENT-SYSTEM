package com.example.feat1.DDD.order_context.domain.port;

import com.example.feat1.DDD.order_context.domain.snapshot.OrderTableSnapshot;
import java.util.UUID;

public interface TableValidationPort {
  OrderTableSnapshot validate(UUID tableId);
}
