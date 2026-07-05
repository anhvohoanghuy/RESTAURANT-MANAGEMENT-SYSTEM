package com.example.feat1.DDD.order_context.domain.snapshot;

import java.util.UUID;

public record OrderTableSnapshot(
    UUID tableId, String code, String name, UUID areaId, String areaName) {}
