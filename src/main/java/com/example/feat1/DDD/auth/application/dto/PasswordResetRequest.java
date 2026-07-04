package com.example.feat1.DDD.auth.application.dto;

public record PasswordResetRequest(String token, String newPassword) {}
