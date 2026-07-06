package com.example.feat1.DDD.order_context.domain.snapshot;

import java.util.UUID;

public record OrderTableSessionSnapshot(UUID sessionId, UUID tableId) {}
