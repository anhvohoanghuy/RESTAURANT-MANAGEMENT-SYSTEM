package com.example.feat1.DDD.auth.infrastructure.cache;

import com.example.feat1.DDD.auth.application.auth_service.refresh.RefreshTokenCache;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisRefreshTokenCache implements RefreshTokenCache {
  private static final String KEY_PREFIX = "auth:refresh-token:";

  private final RedisTemplate<String, Object> redisTemplate;

  @Override
  public boolean contains(String refreshToken) {
    try {
      return Boolean.TRUE.equals(redisTemplate.hasKey(key(refreshToken)));
    } catch (RuntimeException exception) {
      log.warn("Refresh-token Redis cache unavailable; falling back to database", exception);
      return false;
    }
  }

  @Override
  public void put(String refreshToken, UUID userId, Instant expiryDate) {
    Duration ttl = Duration.between(Instant.now(), expiryDate);
    if (ttl.isZero() || ttl.isNegative()) {
      return;
    }

    try {
      redisTemplate.opsForValue().set(key(refreshToken), userId.toString(), ttl);
    } catch (RuntimeException exception) {
      log.warn(
          "Failed to cache refresh token in Redis; database remains source of truth", exception);
    }
  }

  @Override
  public void evict(String refreshToken) {
    try {
      redisTemplate.delete(key(refreshToken));
    } catch (RuntimeException exception) {
      log.warn(
          "Failed to evict refresh token from Redis; database remains source of truth", exception);
    }
  }

  private String key(String refreshToken) {
    return KEY_PREFIX + refreshToken;
  }
}
