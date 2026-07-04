package com.example.feat1.DDD.auth.application.auth_service.google;

public interface GoogleIdTokenVerifier {
  GoogleUserInfo verify(String idToken);
}
