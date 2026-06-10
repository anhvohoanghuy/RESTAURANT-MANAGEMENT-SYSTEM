package com.example.feat1.DDD.identity_context.domain.model.entity;

import com.example.feat1.DDD.identity_context.domain.model.enums.AuthProvider;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Credential {
  private UUID id;
  private UUID userId;
  private String providerUserId;
  private String passwordHash;
  private AuthProvider authProvider;

  public void changePassword(String newHashedPassword) {
    if (this.authProvider != AuthProvider.LOCAL) {
      throw new UnsupportedOperationException("Only local credential supports password change");
    }
    this.passwordHash = newHashedPassword;
  }

  public static Credential createLocal(UUID userId, String username, String hashedPassword) {
    Credential c = new Credential();
    c.id = UUID.randomUUID();
    c.userId = userId;
    c.authProvider = AuthProvider.LOCAL;
    c.providerUserId = username;
    c.passwordHash = hashedPassword;
    return c;
  }

  public static Credential createOAuth(UUID userId, AuthProvider provider, String providerUserId) {
    Credential c = new Credential();
    c.id = UUID.randomUUID();
    c.userId = userId;
    c.authProvider = provider;
    c.providerUserId = providerUserId;
    return c;
  }

  public void validate() {
    if (authProvider == null) {
      throw new IllegalArgumentException("Login type required");
    }
    if (providerUserId == null || providerUserId.isBlank()) {
      throw new IllegalArgumentException("ProviderUserId required");
    }
    if (authProvider == AuthProvider.LOCAL) {
      if (passwordHash == null || passwordHash.isBlank()) {
        throw new IllegalArgumentException("Password required");
      }
    }
  }

  public static Credential create(
      UUID userId, AuthProvider loginType, String providerUserId, String passwordHash) {
    if (loginType == AuthProvider.LOCAL) {
      return createLocal(userId, providerUserId, passwordHash);
    }
    return createOAuth(userId, loginType, providerUserId);
  }
}
