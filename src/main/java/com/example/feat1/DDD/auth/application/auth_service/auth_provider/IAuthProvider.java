package com.example.feat1.DDD.auth.application.auth_service.auth_provider;

import com.example.feat1.DDD.auth.application.dto.AuthRequest;
import com.example.feat1.DDD.auth.application.dto.AuthResponse;

public interface IAuthProvider {
  AuthResponse authenticate(AuthRequest authRequest);
}
