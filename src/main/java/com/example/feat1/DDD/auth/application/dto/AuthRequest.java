package com.example.feat1.DDD.auth.application.dto;

import com.example.feat1.DDD.auth.domain.model.AuthType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {
  private AuthType authType;
  private String username;
  private String password;
  String oathToken;
}
