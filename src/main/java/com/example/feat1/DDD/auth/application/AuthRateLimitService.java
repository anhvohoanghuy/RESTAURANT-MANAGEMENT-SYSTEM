package com.example.feat1.DDD.auth.application;

import com.example.feat1.DDD.auth.application.dto.AuthRequestMetadata;
import com.example.feat1.common.exception.AppException;
import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthRateLimitService {
  private static final int LOGIN_LIMIT = 5;
  private static final Duration LOGIN_WINDOW = Duration.ofMinutes(1);
  private static final int RECOVERY_EMAIL_LIMIT = 3;
  private static final int RECOVERY_IP_LIMIT = 30;
  private static final Duration RECOVERY_WINDOW = Duration.ofHours(1);
  private static final int GOOGLE_IP_LIMIT = 30;
  private static final Duration GOOGLE_WINDOW = Duration.ofMinutes(1);

  private final StringRedisTemplate redisTemplate;

  public void checkLogin(String username, AuthRequestMetadata metadata) {
    check("login", normalize(username) + ":" + ip(metadata), LOGIN_LIMIT, LOGIN_WINDOW);
  }

  public void checkRecovery(String email, AuthRequestMetadata metadata) {
    check("recovery:email", normalize(email), RECOVERY_EMAIL_LIMIT, RECOVERY_WINDOW);
    check("recovery:ip", ip(metadata), RECOVERY_IP_LIMIT, RECOVERY_WINDOW);
  }

  public void checkGoogle(AuthRequestMetadata metadata) {
    check("google:ip", ip(metadata), GOOGLE_IP_LIMIT, GOOGLE_WINDOW);
  }

  public void check(String bucket, String key, int limit, Duration window) {
    String redisKey = "auth:rate:" + bucket + ":" + sanitize(key);
    try {
      Long count = redisTemplate.opsForValue().increment(redisKey);
      if (count != null && count == 1L) {
        redisTemplate.expire(redisKey, window);
      }
      if (count != null && count > limit) {
        throw new AppException(
            "RATE_LIMIT_EXCEEDED", "Rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS);
      }
    } catch (AppException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      log.warn("Auth rate limit check failed open for key={}", redisKey, exception);
    }
  }

  private String ip(AuthRequestMetadata metadata) {
    if (metadata == null || metadata.ipAddress() == null || metadata.ipAddress().isBlank()) {
      return "unknown";
    }
    return metadata.ipAddress();
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) {
      return "blank";
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private String sanitize(String value) {
    return normalize(value).replaceAll("[^a-z0-9@._:-]", "_");
  }
}
