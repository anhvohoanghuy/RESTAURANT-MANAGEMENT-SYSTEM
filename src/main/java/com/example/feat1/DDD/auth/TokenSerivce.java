package com.example.feat1.DDD.auth;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TokenSerivce {
  private final JwtProvider jwtProvider;
  private final IRefreshTokenRepository refreshTokenRepository;
  private final UserService userService;
  private final EntityManager entityManager;
  private final RefreshTokenCache refreshTokenCache;

  @Transactional
  public AuthResponse generateAccessToken(User user) {
    String accessToken = jwtProvider.generateToken(user, TokenType.ACCESS);
    String refreshToken = jwtProvider.generateToken(user, TokenType.REFRESH);
    UserEntity userReference = entityManager.getReference(UserEntity.class, user.getId());
    Instant refreshExpiry = Instant.now().plusMillis(jwtProvider.getExpiration(TokenType.REFRESH));

    RefreshTokenEntity refreshTokenEntity =
        RefreshTokenEntity.active(refreshToken, userReference, refreshExpiry);
    refreshTokenRepository.save(refreshTokenEntity);
    refreshTokenCache.put(refreshToken, user.getId(), refreshExpiry);

    return new AuthResponse(accessToken, refreshToken);
  }

  @Transactional
  public AuthResponse refresh(String refreshToken) {
    if (refreshToken == null || refreshToken.isEmpty()) {
      throw new IllegalArgumentException("Refresh token cannot be null or empty");
    }

    if (!jwtProvider.validateToken(refreshToken)
        || !jwtProvider.isTokenType(refreshToken, TokenType.REFRESH)) {
      throw new RuntimeException("Refresh token is invalid");
    }

    UUID userId = jwtProvider.extractUserId(refreshToken);
    boolean cacheHit = refreshTokenCache.contains(refreshToken);

    Optional<RefreshTokenEntity> storedToken =
        refreshTokenRepository.findByToken(refreshToken);
    if (storedToken.isEmpty()) {
      throw new RuntimeException("Refresh token not found");
    }

    RefreshTokenEntity refreshTokenEntity = storedToken.get();
    UUID storedUserId = refreshTokenEntity.getUser().getId();
    if (!storedUserId.equals(userId)) {
      revokeAllUserRefreshTokens(storedUserId);
      throw new RuntimeException("Refresh token subject does not match stored owner");
    }

    Instant now = Instant.now();
    if (refreshTokenEntity.isRevoked()) {
      revokeAllUserRefreshTokens(userId);
      throw new RuntimeException("Refresh token reuse detected");
    }

    if (refreshTokenEntity.isExpired(now)) {
      refreshTokenEntity.revoke(now);
      refreshTokenRepository.save(refreshTokenEntity);
      refreshTokenCache.evict(refreshToken);
      throw new RuntimeException("Refresh token is expired");
    }

    Optional<User> user = userService.getUserById(userId);

    if (user.isEmpty()) {
      throw new RuntimeException("User not found");
    }

    if (!cacheHit) {
      refreshTokenCache.put(refreshToken, userId, refreshTokenEntity.getExpiryDate());
    }

    String newAccessToken = jwtProvider.generateToken(user.get(), TokenType.ACCESS);
    String newRefreshToken = jwtProvider.generateToken(user.get(), TokenType.REFRESH);
    Instant newRefreshExpiry = now.plusMillis(jwtProvider.getExpiration(TokenType.REFRESH));
    UserEntity userReference = entityManager.getReference(UserEntity.class, userId);

    refreshTokenEntity.rotateTo(newRefreshToken, now);
    refreshTokenRepository.save(refreshTokenEntity);
    RefreshTokenEntity newRefreshTokenEntity =
        RefreshTokenEntity.active(newRefreshToken, userReference, newRefreshExpiry);
    refreshTokenRepository.save(newRefreshTokenEntity);
    refreshTokenCache.evict(refreshToken);
    refreshTokenCache.put(newRefreshToken, userId, newRefreshExpiry);

    return new AuthResponse(newAccessToken, newRefreshToken);
  }

  @Transactional
  public void logout(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      return;
    }

    Instant now = Instant.now();
    refreshTokenRepository
        .findByToken(refreshToken)
        .ifPresent(
            token -> {
              token.revoke(now);
              refreshTokenRepository.save(token);
            });
    refreshTokenCache.evict(refreshToken);
  }

  private void revokeAllUserRefreshTokens(UUID userId) {
    Instant now = Instant.now();
    List<RefreshTokenEntity> activeTokens =
        refreshTokenRepository.findAllByUser_IdAndRevokedAtIsNull(userId);
    activeTokens.forEach(
        token -> {
          token.revoke(now);
          refreshTokenCache.evict(token.getToken());
        });
    refreshTokenRepository.saveAll(activeTokens);
  }
}
