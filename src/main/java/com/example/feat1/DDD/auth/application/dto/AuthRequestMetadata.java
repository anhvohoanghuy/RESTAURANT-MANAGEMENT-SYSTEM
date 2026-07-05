package com.example.feat1.DDD.auth.application.dto;

public record AuthRequestMetadata(String ipAddress, String userAgent) {
  public static AuthRequestMetadata empty() {
    return new AuthRequestMetadata(null, null);
  }
}
