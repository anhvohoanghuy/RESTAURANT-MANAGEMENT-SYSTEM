package com.example.feat1.DDD.auth.application;

import com.example.feat1.DDD.auth.application.auth_service.refresh.RefreshTokenCache;
import com.example.feat1.DDD.auth.application.dto.AuthSessionResponse;
import com.example.feat1.DDD.auth.infrastructure.entity.RefreshTokenEntity;
import com.example.feat1.DDD.identity_context.infastructure.repository.IRefreshTokenRepository;
import com.example.feat1.common.exception.AppException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthSessionService {
  private final IRefreshTokenRepository refreshTokenRepository;
  private final RefreshTokenCache refreshTokenCache;

  @Transactional(readOnly = true)
  public List<AuthSessionResponse> listActiveSessions(UUID userId) {
    Instant now = Instant.now();
    return refreshTokenRepository
        .findAllByUser_IdAndRevokedAtIsNullAndExpiryDateAfter(userId, now)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public void revokeSession(UUID userId, UUID sessionId) {
    RefreshTokenEntity session =
        refreshTokenRepository
            .findByIdAndUser_Id(sessionId, userId)
            .orElseThrow(
                () ->
                    new AppException(
                        "SESSION_NOT_FOUND", "Session not found", HttpStatus.NOT_FOUND));
    Instant now = Instant.now();
    if (!session.isRevoked()) {
      session.revoke(now);
      refreshTokenRepository.save(session);
      refreshTokenCache.evict(session.getToken());
    }
  }

  @Transactional
  public void revokeOtherSessions(UUID userId, String currentRefreshToken) {
    if (currentRefreshToken == null || currentRefreshToken.isBlank()) {
      throw new AppException(
          "REFRESH_TOKEN_REQUIRED", "Refresh token is required", HttpStatus.BAD_REQUEST);
    }

    RefreshTokenEntity currentSession =
        refreshTokenRepository
            .findByToken(currentRefreshToken)
            .filter(token -> token.getUser().getId().equals(userId))
            .filter(token -> token.isUsable(Instant.now()))
            .orElseThrow(
                () ->
                    new AppException(
                        "REFRESH_TOKEN_INVALID",
                        "Refresh token is invalid",
                        HttpStatus.UNAUTHORIZED));

    Instant now = Instant.now();
    List<RefreshTokenEntity> activeSessions =
        refreshTokenRepository.findAllByUser_IdAndRevokedAtIsNullAndExpiryDateAfter(userId, now);
    activeSessions.stream()
        .filter(session -> !session.getId().equals(currentSession.getId()))
        .forEach(
            session -> {
              session.revoke(now);
              refreshTokenCache.evict(session.getToken());
            });
    refreshTokenRepository.saveAll(activeSessions);
  }

  private AuthSessionResponse toResponse(RefreshTokenEntity session) {
    return new AuthSessionResponse(
        session.getId(),
        session.getCreatedAt(),
        session.getExpiryDate(),
        session.getLastUsedAt(),
        session.getIpAddress(),
        session.getUserAgent());
  }
}
