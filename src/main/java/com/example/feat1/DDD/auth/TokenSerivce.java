package com.example.feat1.DDD.auth;

import com.example.feat1.DDD.auth.application.dto.AuthResponse;
import com.example.feat1.DDD.auth.application.auth_service.jwt.JwtProvider;
import com.example.feat1.DDD.auth.application.auth_service.jwt.TokenType;
import com.example.feat1.DDD.identity_context.infastructure.mapper.UserService;
import com.example.feat1.DDD.identity_context.domain.model.aggregate.User;
import com.example.feat1.DDD.identity_context.infastructure.repository.IRefreshTokenRepository;
import com.example.feat1.DDD.auth.infrastructure.entity.RefreshTokenEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenSerivce {
    private final JwtProvider jwtProvider;
    private final IRefreshTokenRepository refreshTokenRepository;
    private final UserService userService;

    public AuthResponse generateAccessToken(User user){
        String accessToken = jwtProvider.generateToken(user, TokenType.ACCESS);
        String refreshToken = jwtProvider.generateToken(user, TokenType.REFRESH);

        return new AuthResponse(accessToken, refreshToken);
    }

    public AuthResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new IllegalArgumentException("Refresh token cannot be null or empty");
        }

        if (!jwtProvider.validateToken(refreshToken)){
            throw new RuntimeException("Refresh token is invalid");
        }

        UUID userId = jwtProvider.extractUserId(refreshToken);

        Optional<RefreshTokenEntity> refreshTokenEntity = refreshTokenRepository.findByToken(refreshToken);
        if(refreshTokenEntity.isEmpty()){
            throw new RuntimeException("Refresh token not found");
        }

        Optional<User> user = userService.getUserById(userId);

        if(user.isEmpty()){
            throw new RuntimeException("User not found");
        }

        String newAccessToken = jwtProvider.generateToken(user.get(), TokenType.ACCESS);

        return new AuthResponse(newAccessToken, refreshToken);
    }
}
