package com.example.feat1.DDD.auth.application.auth_service.auth_provider;

import com.example.feat1.DDD.auth.application.dto.AuthRequest;
import com.example.feat1.DDD.auth.application.dto.AuthResponse;
import org.springframework.stereotype.Component;

@Component("GOOGLE")
public class GoogleAuthProvider implements IAuthProvider {

  @Override
  public AuthResponse authenticate(AuthRequest authRequest) {
    throw new UnsupportedOperationException("Google authentication is not implemented yet");
  }
}
