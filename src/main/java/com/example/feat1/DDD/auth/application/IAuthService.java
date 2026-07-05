package com.example.feat1.DDD.auth.application;

import com.example.feat1.DDD.auth.application.dto.AuthRequest;
import com.example.feat1.DDD.auth.application.dto.AuthRequestMetadata;
import com.example.feat1.DDD.auth.application.dto.AuthResponse;

public interface IAuthService {
  public AuthResponse login(AuthRequest authRequest);

  public AuthResponse refreshToken(String refreshToken);

  public AuthResponse refreshToken(String refreshToken, AuthRequestMetadata metadata);

  public void logout(String refreshToken);
}
