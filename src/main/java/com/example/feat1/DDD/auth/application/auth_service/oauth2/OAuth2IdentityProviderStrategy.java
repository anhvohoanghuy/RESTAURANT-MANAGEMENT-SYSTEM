package com.example.feat1.DDD.auth.application.auth_service.oauth2;

import com.example.feat1.DDD.identity_context.domain.model.enums.AuthProvider;
import org.springframework.http.HttpStatus;

public interface OAuth2IdentityProviderStrategy {
  AuthProvider provider();

  OAuth2UserInfo verify(String token);

  boolean canAutoLink(OAuth2UserInfo userInfo);

  default String tokenRequiredCode() {
    return provider().name() + "_TOKEN_REQUIRED";
  }

  default String tokenRequiredMessage() {
    return providerDisplayName() + " token is required";
  }

  default HttpStatus tokenRequiredStatus() {
    return HttpStatus.BAD_REQUEST;
  }

  default String emailUnverifiedCode() {
    return provider().name() + "_EMAIL_UNVERIFIED";
  }

  default String emailUnverifiedMessage() {
    return providerDisplayName() + " email is not verified";
  }

  default HttpStatus emailUnverifiedStatus() {
    return HttpStatus.UNAUTHORIZED;
  }

  default String emailNotLinkableCode() {
    return provider().name() + "_EMAIL_NOT_LINKABLE";
  }

  default String emailNotLinkableMessage() {
    return providerDisplayName() + " email is not eligible for automatic linking";
  }

  default HttpStatus emailNotLinkableStatus() {
    return HttpStatus.CONFLICT;
  }

  private String providerDisplayName() {
    String lower = provider().name().toLowerCase();
    return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
  }
}
