package com.example.feat1.DDD.auth.application.dto;

import java.time.Instant;
import java.util.UUID;

public record AuthSessionResponse(
    UUID sessionId,
    Instant createdAt,
    Instant expiresAt,
    Instant lastUsedAt,
    String ipAddress,
    String userAgent) {}
