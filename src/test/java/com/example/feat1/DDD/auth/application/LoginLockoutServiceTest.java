package com.example.feat1.DDD.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.common.exception.AppException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class LoginLockoutServiceTest {
  private final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
  private final ValueOperations<String, String> valueOperations =
      Mockito.mock(ValueOperations.class);
  private final LoginLockoutService service = new LoginLockoutService(redisTemplate);

  @Test
  void throwsAccountLockedWhenLockKeyExists() {
    when(redisTemplate.hasKey("auth:lockout:locked:chinh")).thenReturn(true);

    assertThatThrownBy(() -> service.checkNotLocked("chinh"))
        .isInstanceOf(AppException.class)
        .hasMessage("Account is temporarily locked");
  }

  @Test
  void locksAccountAtFailureThreshold() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment("auth:lockout:failures:chinh")).thenReturn(5L);

    assertThatThrownBy(() -> service.recordFailure("chinh"))
        .isInstanceOf(AppException.class)
        .hasMessage("Account is temporarily locked");

    verify(valueOperations).set("auth:lockout:locked:chinh", "1", Duration.ofMinutes(15));
    verify(redisTemplate).delete("auth:lockout:failures:chinh");
  }

  @Test
  void clearsFailureAndLockKeysOnSuccess() {
    service.recordSuccess("chinh");

    verify(redisTemplate).delete("auth:lockout:failures:chinh");
    verify(redisTemplate).delete("auth:lockout:locked:chinh");
  }

  @Test
  void failsOpenWhenRedisFails() {
    when(redisTemplate.hasKey(anyString())).thenThrow(new IllegalStateException("redis down"));

    service.checkNotLocked("chinh");
  }
}
