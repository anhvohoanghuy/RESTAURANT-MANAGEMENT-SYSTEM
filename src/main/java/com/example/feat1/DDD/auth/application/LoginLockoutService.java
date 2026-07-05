package com.example.feat1.DDD.auth.application;

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
public class LoginLockoutService {
  private static final int FAILURE_LIMIT = 5;
  private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
  private static final Duration LOCKOUT_WINDOW = Duration.ofMinutes(15);

  private final StringRedisTemplate redisTemplate;

  public void checkNotLocked(String username) {
    String lockKey = lockKey(username);
    try {
      Boolean locked = redisTemplate.hasKey(lockKey);
      if (Boolean.TRUE.equals(locked)) {
        throw accountLocked();
      }
    } catch (AppException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      log.warn("Login lockout check failed open for key={}", lockKey, exception);
    }
  }

  public void recordFailure(String username) {
    String failureKey = failureKey(username);
    String lockKey = lockKey(username);
    try {
      Long failures = redisTemplate.opsForValue().increment(failureKey);
      if (failures != null && failures == 1L) {
        redisTemplate.expire(failureKey, FAILURE_WINDOW);
      }
      if (failures != null && failures >= FAILURE_LIMIT) {
        redisTemplate.opsForValue().set(lockKey, "1", LOCKOUT_WINDOW);
        redisTemplate.delete(failureKey);
        throw accountLocked();
      }
    } catch (AppException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      log.warn("Login lockout failure record failed open for key={}", failureKey, exception);
    }
  }

  public void recordSuccess(String username) {
    try {
      redisTemplate.delete(failureKey(username));
      redisTemplate.delete(lockKey(username));
    } catch (RuntimeException exception) {
      log.warn("Login lockout success cleanup failed for username={}", username, exception);
    }
  }

  private AppException accountLocked() {
    return new AppException("ACCOUNT_LOCKED", "Account is temporarily locked", HttpStatus.LOCKED);
  }

  private String failureKey(String username) {
    return "auth:lockout:failures:" + normalize(username);
  }

  private String lockKey(String username) {
    return "auth:lockout:locked:" + normalize(username);
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) {
      return "blank";
    }
    return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9@._:-]", "_");
  }
}
