package com.example.feat1.DDD.auth.application.auth_service.google;

public record GoogleUserInfo(
    String subject, String email, boolean emailVerified, String name, String hostedDomain) {}
