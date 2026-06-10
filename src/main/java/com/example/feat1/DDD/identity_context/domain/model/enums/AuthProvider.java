package com.example.feat1.DDD.identity_context.domain.model.enums;

public enum AuthProvider {
  LOCAL,
  GOOGLE,
  FACEBOOK,
  GITHUB,
  APPLE;

  public static AuthProvider fromString(String name) {
    return AuthProvider.valueOf(name.toUpperCase());
  }
}
