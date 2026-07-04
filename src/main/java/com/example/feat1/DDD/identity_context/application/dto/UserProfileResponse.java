package com.example.feat1.DDD.identity_context.application.dto;

import java.util.Set;
import java.util.UUID;

public record UserProfileResponse(UUID id, String name, String email, Set<String> roles) {}
