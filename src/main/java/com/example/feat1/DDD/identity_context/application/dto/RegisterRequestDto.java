package com.example.feat1.DDD.identity_context.application.dto;

import com.example.feat1.DDD.identity_context.domain.model.enums.AuthProvider;
import lombok.Data;

import java.util.List;

@Data
public class RegisterRequestDto {
    String username;
    String email;
    String password;
    String providerUserId;
    AuthProvider loginType;
    List<String> Roles;
}
