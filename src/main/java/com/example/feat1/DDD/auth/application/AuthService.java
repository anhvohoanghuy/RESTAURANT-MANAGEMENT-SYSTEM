package com.example.feat1.DDD.auth.application;

import com.example.feat1.DDD.auth.TokenSerivce;
import com.example.feat1.DDD.auth.application.auth_service.auth_provider.IAuthProvider;
import com.example.feat1.DDD.auth.application.dto.AuthRequest;
import com.example.feat1.DDD.auth.application.dto.AuthRequestMetadata;
import com.example.feat1.DDD.auth.application.dto.AuthResponse;
import com.example.feat1.common.exception.AppException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AuthService implements IAuthService {
  private final Map<String, IAuthProvider> authProviderMap;
  private final TokenSerivce tokenSerivce;

  @Override
  public AuthResponse login(AuthRequest authRequest) {
    if (authRequest == null || authRequest.getAuthType() == null) {
      throw new AppException("AUTH_TYPE_REQUIRED", "Auth type is required", HttpStatus.BAD_REQUEST);
    }

    IAuthProvider provider = authProviderMap.get(authRequest.getAuthType().name());
    if (provider == null) {
      throw new AppException(
          "AUTH_PROVIDER_NOT_SUPPORTED", "Auth provider is not supported", HttpStatus.BAD_REQUEST);
    }
    return provider.authenticate(authRequest);
  }

  @Override
  public AuthResponse refreshToken(String refreshToken) {
    return tokenSerivce.refresh(refreshToken);
  }

  @Override
  public AuthResponse refreshToken(String refreshToken, AuthRequestMetadata metadata) {
    return tokenSerivce.refresh(refreshToken, metadata);
  }

  @Override
  public void logout(String refreshToken) {
    tokenSerivce.logout(refreshToken);
  }
}
