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
import com.example.feat1.common.exception.AppException;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TokenSerivce {
  private static final String BEARER_TOKEN_TYPE = "Bearer";

  private final JwtProvider jwtProvider;
  private final IRefreshTokenRepository refreshTokenRepository;
  private final UserService userService;
  private final EntityManager entityManager;
  private final RefreshTokenCache refreshTokenCache;

  @Transactional
  public AuthResponse generateAccessToken(User user) {
    String accessToken = jwtProvider.generateToken(user, TokenType.ACCESS);
    String refreshToken = jwtProvider.generateToken(user, TokenType.REFRESH);
    long accessExpiration = jwtProvider.getExpiration(TokenType.ACCESS);
    long refreshExpiration = jwtProvider.getExpiration(TokenType.REFRESH);
    UserEntity userReference = entityManager.getReference(UserEntity.class, user.getId());
    Instant refreshExpiry = Instant.now().plusMillis(refreshExpiration);

    RefreshTokenEntity refreshTokenEntity =
        RefreshTokenEntity.active(refreshToken, userReference, refreshExpiry);
    refreshTokenRepository.save(refreshTokenEntity);
    refreshTokenCache.put(refreshToken, user.getId(), refreshExpiry);

    return new AuthResponse(
        accessToken, refreshToken, BEARER_TOKEN_TYPE, accessExpiration, refreshExpiration);
  }

  @Transactional
  public AuthResponse refresh(String refreshToken) {
    if (refreshToken == null || refreshToken.isEmpty()) {
      throw new AppException(
          "REFRESH_TOKEN_REQUIRED", "Refresh token is required", HttpStatus.BAD_REQUEST);
    }

    if (!jwtProvider.validateToken(refreshToken)
        || !jwtProvider.isTokenType(refreshToken, TokenType.REFRESH)) {
      throw new AppException(
          "REFRESH_TOKEN_INVALID", "Refresh token is invalid", HttpStatus.UNAUTHORIZED);
    }

    UUID userId = jwtProvider.extractUserId(refreshToken);
    boolean cacheHit = refreshTokenCache.contains(refreshToken);

    Optional<RefreshTokenEntity> storedToken = refreshTokenRepository.findByToken(refreshToken);
    if (storedToken.isEmpty()) {
      throw new AppException(
          "REFRESH_TOKEN_REVOKED", "Refresh token is revoked", HttpStatus.UNAUTHORIZED);
    }

    RefreshTokenEntity refreshTokenEntity = storedToken.get();
    UUID storedUserId = refreshTokenEntity.getUser().getId();
    if (!storedUserId.equals(userId)) {
      revokeAllUserRefreshTokens(storedUserId);
      throw new AppException(
          "REFRESH_TOKEN_REUSED", "Refresh token reuse detected", HttpStatus.UNAUTHORIZED);
    }

    Instant now = Instant.now();
    if (refreshTokenEntity.isRevoked()) {
      revokeAllUserRefreshTokens(userId);
      throw new AppException(
          "REFRESH_TOKEN_REUSED", "Refresh token reuse detected", HttpStatus.UNAUTHORIZED);
    }

    if (refreshTokenEntity.isExpired(now)) {
      refreshTokenEntity.revoke(now);
      refreshTokenRepository.save(refreshTokenEntity);
      refreshTokenCache.evict(refreshToken);
      throw new AppException(
          "REFRESH_TOKEN_EXPIRED", "Refresh token is expired", HttpStatus.UNAUTHORIZED);
    }

    Optional<User> user = userService.getUserById(userId);

    if (user.isEmpty()) {
      throw new AppException("USER_NOT_FOUND", "User not found", HttpStatus.UNAUTHORIZED);
    }

    if (!cacheHit) {
      refreshTokenCache.put(refreshToken, userId, refreshTokenEntity.getExpiryDate());
    }

    String newAccessToken = jwtProvider.generateToken(user.get(), TokenType.ACCESS);
    String newRefreshToken = jwtProvider.generateToken(user.get(), TokenType.REFRESH);
    long accessExpiration = jwtProvider.getExpiration(TokenType.ACCESS);
    long refreshExpiration = jwtProvider.getExpiration(TokenType.REFRESH);
    Instant newRefreshExpiry = now.plusMillis(refreshExpiration);
    UserEntity userReference = entityManager.getReference(UserEntity.class, userId);

    refreshTokenEntity.rotateTo(newRefreshToken, now);
    refreshTokenRepository.save(refreshTokenEntity);
    RefreshTokenEntity newRefreshTokenEntity =
        RefreshTokenEntity.active(newRefreshToken, userReference, newRefreshExpiry);
    refreshTokenRepository.save(newRefreshTokenEntity);
    refreshTokenCache.evict(refreshToken);
    refreshTokenCache.put(newRefreshToken, userId, newRefreshExpiry);

    return new AuthResponse(
        newAccessToken, newRefreshToken, BEARER_TOKEN_TYPE, accessExpiration, refreshExpiration);
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
