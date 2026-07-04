package com.example.feat1.DDD.auth.application.auth_service.refresh;

import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenCache {
  boolean contains(String refreshToken);

  void put(String refreshToken, UUID userId, Instant expiryDate);

  void evict(String refreshToken);
}
