package com.example.feat1.DDD.identity_context.application.dto;

import com.example.feat1.DDD.identity_context.domain.model.enums.AuthProvider;
import java.util.List;
import lombok.Data;

@Data
public class RegisterRequestDto {
  private String username;
  private String email;
  private String password;
  private String providerUserId;
  private AuthProvider loginType;
  private List<String> roles;
}
