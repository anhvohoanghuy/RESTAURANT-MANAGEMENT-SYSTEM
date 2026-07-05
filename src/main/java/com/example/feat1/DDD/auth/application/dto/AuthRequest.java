package com.example.feat1.DDD.auth.application.dto;

import com.example.feat1.DDD.auth.domain.model.AuthType;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AuthRequest {
  private AuthType authType;
  private String username;
  private String password;
  private String oathToken;
  private AuthRequestMetadata metadata;

  public AuthRequest(AuthType authType, String username, String password, String oathToken) {
    this(authType, username, password, oathToken, AuthRequestMetadata.empty());
  }

  public AuthRequest(
      AuthType authType,
      String username,
      String password,
      String oathToken,
      AuthRequestMetadata metadata) {
    this.authType = authType;
    this.username = username;
    this.password = password;
    this.oathToken = oathToken;
    this.metadata = metadata == null ? AuthRequestMetadata.empty() : metadata;
  }
}
