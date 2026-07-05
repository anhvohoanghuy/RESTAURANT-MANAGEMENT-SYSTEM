package com.example.feat1.DDD.order_context.domain.port;

import com.example.feat1.DDD.order_context.domain.snapshot.OrderMenuQuote;
import java.util.List;
import java.util.UUID;

public interface MenuQuotePort {
  OrderMenuQuote quote(UUID dishId, List<UUID> toppingOptionIds);
}
