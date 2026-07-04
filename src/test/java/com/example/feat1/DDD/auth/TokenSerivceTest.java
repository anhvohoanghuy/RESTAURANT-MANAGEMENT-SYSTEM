package com.example.feat1.DDD.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.auth.application.auth_service.jwt.JwtProvider;
import com.example.feat1.DDD.auth.application.auth_service.jwt.TokenType;
import com.example.feat1.DDD.auth.application.auth_service.refresh.RefreshTokenCache;
import com.example.feat1.DDD.auth.application.dto.AuthResponse;
import com.example.feat1.DDD.auth.infrastructure.entity.RefreshTokenEntity;
import com.example.feat1.DDD.identity_context.domain.model.aggregate.User;
import com.example.feat1.DDD.identity_context.infastructure.entity.UserEntity;
import com.example.feat1.DDD.identity_context.infastructure.mapper.UserService;
import com.example.feat1.DDD.identity_context.infastructure.repository.IRefreshTokenRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenSerivceTest {
  private JwtProvider jwtProvider;
  private IRefreshTokenRepository refreshTokenRepository;
  private UserService userService;
  private EntityManager entityManager;
  private RefreshTokenCache refreshTokenCache;
  private TokenSerivce tokenSerivce;

  @BeforeEach
  void setUp() {
    jwtProvider = mock(JwtProvider.class);
    refreshTokenRepository = mock(IRefreshTokenRepository.class);
    userService = mock(UserService.class);
    entityManager = mock(EntityManager.class);
    refreshTokenCache = mock(RefreshTokenCache.class);
    tokenSerivce =
        new TokenSerivce(
            jwtProvider, refreshTokenRepository, userService, entityManager, refreshTokenCache);
  }

  @Test
  void refreshRotatesRefreshTokenAndUsesDatabaseFallbackWhenRedisMisses() {
    UUID userId = UUID.randomUUID();
    User user = new User(userId, "Chinh", "chinh@example.com", new java.util.HashSet<>());
    UserEntity userEntity = new UserEntity();
    userEntity.setId(userId);
    RefreshTokenEntity oldToken =
        RefreshTokenEntity.active("old-refresh", userEntity, Instant.now().plusSeconds(300));

    when(jwtProvider.validateToken("old-refresh")).thenReturn(true);
    when(jwtProvider.isTokenType("old-refresh", TokenType.REFRESH)).thenReturn(true);
    when(jwtProvider.extractUserId("old-refresh")).thenReturn(userId);
    when(jwtProvider.generateToken(user, TokenType.ACCESS)).thenReturn("new-access");
    when(jwtProvider.generateToken(user, TokenType.REFRESH)).thenReturn("new-refresh");
    when(jwtProvider.getExpiration(TokenType.REFRESH)).thenReturn(60_000L);
    when(refreshTokenCache.contains("old-refresh")).thenReturn(false);
    when(refreshTokenRepository.findByToken("old-refresh")).thenReturn(Optional.of(oldToken));
    when(userService.getUserById(userId)).thenReturn(Optional.of(user));
    when(entityManager.getReference(UserEntity.class, userId)).thenReturn(userEntity);

    AuthResponse response = tokenSerivce.refresh("old-refresh");

    assertThat(response.getAccessToken()).isEqualTo("new-access");
    assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
    assertThat(oldToken.isRevoked()).isTrue();
    assertThat(oldToken.getReplacedByToken()).isEqualTo("new-refresh");
    verify(refreshTokenCache).put("old-refresh", userId, oldToken.getExpiryDate());
    verify(refreshTokenCache).evict("old-refresh");
    verify(refreshTokenCache).put(eq("new-refresh"), eq(userId), any(Instant.class));
    verify(refreshTokenRepository).save(oldToken);
    verify(refreshTokenRepository)
        .save(argThat(token -> "new-refresh".equals(token.getToken()) && !token.isRevoked()));
  }

  @Test
  void refreshTokenReuseRevokesAllActiveUserSessions() {
    UUID userId = UUID.randomUUID();
    UserEntity userEntity = new UserEntity();
    userEntity.setId(userId);
    RefreshTokenEntity reusedToken =
        RefreshTokenEntity.active("reused-refresh", userEntity, Instant.now().plusSeconds(300));
    reusedToken.revoke(Instant.now());
    RefreshTokenEntity activeTokenOne =
        RefreshTokenEntity.active("active-one", userEntity, Instant.now().plusSeconds(300));
    RefreshTokenEntity activeTokenTwo =
        RefreshTokenEntity.active("active-two", userEntity, Instant.now().plusSeconds(300));

    when(jwtProvider.validateToken("reused-refresh")).thenReturn(true);
    when(jwtProvider.isTokenType("reused-refresh", TokenType.REFRESH)).thenReturn(true);
    when(jwtProvider.extractUserId("reused-refresh")).thenReturn(userId);
    when(refreshTokenRepository.findByToken("reused-refresh")).thenReturn(Optional.of(reusedToken));
    when(refreshTokenRepository.findAllByUser_IdAndRevokedAtIsNull(userId))
        .thenReturn(List.of(activeTokenOne, activeTokenTwo));

    assertThatThrownBy(() -> tokenSerivce.refresh("reused-refresh"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("reuse detected");

    assertThat(activeTokenOne.isRevoked()).isTrue();
    assertThat(activeTokenTwo.isRevoked()).isTrue();
    verify(refreshTokenCache).evict("active-one");
    verify(refreshTokenCache).evict("active-two");
    verify(refreshTokenRepository).saveAll(List.of(activeTokenOne, activeTokenTwo));
  }

  @Test
  void logoutRevokesRefreshTokenInDatabaseAndEvictsRedisCache() {
    UUID userId = UUID.randomUUID();
    UserEntity userEntity = new UserEntity();
    userEntity.setId(userId);
    RefreshTokenEntity activeToken =
        RefreshTokenEntity.active("logout-refresh", userEntity, Instant.now().plusSeconds(300));

    when(refreshTokenRepository.findByToken("logout-refresh")).thenReturn(Optional.of(activeToken));

    tokenSerivce.logout("logout-refresh");

    assertThat(activeToken.isRevoked()).isTrue();
    verify(refreshTokenRepository).save(activeToken);
    verify(refreshTokenCache).evict("logout-refresh");
  }
}
