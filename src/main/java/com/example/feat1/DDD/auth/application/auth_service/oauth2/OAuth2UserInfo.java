package com.example.feat1.DDD.auth.application.auth_service.oauth2;

public record OAuth2UserInfo(
    String providerUserId,
    String email,
    boolean emailVerified,
    String displayName,
    String hostedDomain) {}
