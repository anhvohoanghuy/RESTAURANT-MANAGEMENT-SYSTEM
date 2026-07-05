package com.example.feat1.DDD.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.auth.application.auth_service.refresh.RefreshTokenCache;
import com.example.feat1.DDD.auth.application.dto.AuthRequestMetadata;
import com.example.feat1.DDD.auth.infrastructure.entity.RefreshTokenEntity;
import com.example.feat1.DDD.identity_context.infastructure.entity.UserEntity;
import com.example.feat1.DDD.identity_context.infastructure.repository.IRefreshTokenRepository;
import com.example.feat1.common.exception.AppException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AuthSessionServiceTest {
  private final IRefreshTokenRepository repository = Mockito.mock(IRefreshTokenRepository.class);
  private final RefreshTokenCache refreshTokenCache = Mockito.mock(RefreshTokenCache.class);
  private final AuthSessionService service = new AuthSessionService(repository, refreshTokenCache);

  @Test
  void listsActiveSessionsWithoutRefreshTokenValue() {
    UUID userId = UUID.randomUUID();
    RefreshTokenEntity token =
        refreshToken("refresh-token", userId, new AuthRequestMetadata("10.0.0.1", "JUnit"));
    when(repository.findAllByUser_IdAndRevokedAtIsNullAndExpiryDateAfter(
            Mockito.eq(userId), Mockito.any()))
        .thenReturn(List.of(token));

    var sessions = service.listActiveSessions(userId);

    assertThat(sessions).hasSize(1);
    assertThat(sessions.get(0).sessionId()).isEqualTo(token.getId());
    assertThat(sessions.get(0).ipAddress()).isEqualTo("10.0.0.1");
    assertThat(sessions.get(0).userAgent()).isEqualTo("JUnit");
    assertThat(sessions.toString()).doesNotContain("refresh-token");
  }

  @Test
  void revokesOnlySessionOwnedByCurrentUser() {
    UUID userId = UUID.randomUUID();
    RefreshTokenEntity token = refreshToken("refresh-token", userId, AuthRequestMetadata.empty());
    when(repository.findByIdAndUser_Id(token.getId(), userId)).thenReturn(Optional.of(token));

    service.revokeSession(userId, token.getId());

    assertThat(token.isRevoked()).isTrue();
    verify(refreshTokenCache).evict("refresh-token");
    verify(repository).save(token);
  }

  @Test
  void rejectsSessionOwnedByAnotherUser() {
    UUID userId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    when(repository.findByIdAndUser_Id(sessionId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.revokeSession(userId, sessionId))
        .isInstanceOf(AppException.class)
        .hasMessage("Session not found");
  }

  @Test
  void revokeOthersKeepsCurrentRefreshSession() {
    UUID userId = UUID.randomUUID();
    RefreshTokenEntity current = refreshToken("current", userId, AuthRequestMetadata.empty());
    RefreshTokenEntity other = refreshToken("other", userId, AuthRequestMetadata.empty());
    when(repository.findByToken("current")).thenReturn(Optional.of(current));
    when(repository.findAllByUser_IdAndRevokedAtIsNullAndExpiryDateAfter(
            Mockito.eq(userId), Mockito.any()))
        .thenReturn(List.of(current, other));

    service.revokeOtherSessions(userId, "current");

    assertThat(current.isRevoked()).isFalse();
    assertThat(other.isRevoked()).isTrue();
    verify(refreshTokenCache).evict("other");
    ArgumentCaptor<List<RefreshTokenEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(repository).saveAll(captor.capture());
    assertThat(captor.getValue()).containsExactly(current, other);
  }

  private RefreshTokenEntity refreshToken(String token, UUID userId, AuthRequestMetadata metadata) {
    UserEntity user = new UserEntity();
    user.setId(userId);
    RefreshTokenEntity entity =
        RefreshTokenEntity.active(token, user, Instant.now().plusSeconds(3600), metadata);
    entity.setId(UUID.randomUUID());
    return entity;
  }
}
