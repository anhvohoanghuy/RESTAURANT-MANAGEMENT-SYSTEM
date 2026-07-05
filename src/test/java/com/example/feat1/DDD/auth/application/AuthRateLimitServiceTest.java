package com.example.feat1.DDD.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.auth.application.dto.AuthRequestMetadata;
import com.example.feat1.common.exception.AppException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class AuthRateLimitServiceTest {
  private final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
  private final ValueOperations<String, String> valueOperations =
      Mockito.mock(ValueOperations.class);
  private final AuthRateLimitService service = new AuthRateLimitService(redisTemplate);

  @Test
  void incrementsCounterAndSetsExpiryOnFirstHit() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(anyString())).thenReturn(1L);

    service.checkLogin("chinh", new AuthRequestMetadata("10.0.0.1", "JUnit"));

    verify(valueOperations).increment("auth:rate:login:chinh:10.0.0.1");
    verify(redisTemplate).expire(anyString(), any(Duration.class));
  }

  @Test
  void throwsStableRateLimitCodeWhenLimitExceeded() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(anyString())).thenReturn(6L);

    assertThatThrownBy(
            () -> service.checkLogin("chinh", new AuthRequestMetadata("10.0.0.1", "JUnit")))
        .isInstanceOf(AppException.class)
        .hasMessage("Rate limit exceeded");
  }

  @Test
  void failsOpenWhenRedisFails() {
    when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis down"));

    service.checkLogin("chinh", new AuthRequestMetadata("10.0.0.1", "JUnit"));
  }
}
