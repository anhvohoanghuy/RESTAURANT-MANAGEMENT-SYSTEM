package com.example.feat1.DDD.auth;

import com.example.feat1.DDD.auth.application.auth_service.jwt.JwtProvider;
import com.example.feat1.DDD.auth.application.auth_service.jwt.TokenType;
import com.example.feat1.DDD.auth.application.dto.AuthResponse;
import com.example.feat1.DDD.auth.infrastructure.entity.RefreshTokenEntity;
import com.example.feat1.DDD.identity_context.domain.model.aggregate.User;
import com.example.feat1.DDD.identity_context.infastructure.entity.UserEntity;
import com.example.feat1.DDD.identity_context.infastructure.mapper.UserService;
import com.example.feat1.DDD.identity_context.infastructure.repository.IRefreshTokenRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenSerivce {
  private final JwtProvider jwtProvider;
  private final IRefreshTokenRepository refreshTokenRepository;
  private final UserService userService;
  private final EntityManager entityManager;

  public AuthResponse generateAccessToken(User user) {
    String accessToken = jwtProvider.generateToken(user, TokenType.ACCESS);
    String refreshToken = jwtProvider.generateToken(user, TokenType.REFRESH);
    UserEntity userReference = entityManager.getReference(UserEntity.class, user.getId());

    RefreshTokenEntity refreshTokenEntity =
        new RefreshTokenEntity(
            null,
            refreshToken,
            userReference,
            Instant.now().plusMillis(jwtProvider.getExpiration(TokenType.REFRESH)));
    refreshTokenRepository.save(refreshTokenEntity);

    return new AuthResponse(accessToken, refreshToken);
  }

  public AuthResponse refresh(String refreshToken) {
    if (refreshToken == null || refreshToken.isEmpty()) {
      throw new IllegalArgumentException("Refresh token cannot be null or empty");
    }

    if (!jwtProvider.validateToken(refreshToken)) {
      throw new RuntimeException("Refresh token is invalid");
    }

    UUID userId = jwtProvider.extractUserId(refreshToken);

    Optional<RefreshTokenEntity> refreshTokenEntity =
        refreshTokenRepository.findByToken(refreshToken);
    if (refreshTokenEntity.isEmpty()) {
      throw new RuntimeException("Refresh token not found");
    }

    Optional<User> user = userService.getUserById(userId);

    if (user.isEmpty()) {
      throw new RuntimeException("User not found");
    }

    String newAccessToken = jwtProvider.generateToken(user.get(), TokenType.ACCESS);

    return new AuthResponse(newAccessToken, refreshToken);
  }

  public void logout(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      return;
    }

    refreshTokenRepository.findByToken(refreshToken).ifPresent(refreshTokenRepository::delete);
  }
}
