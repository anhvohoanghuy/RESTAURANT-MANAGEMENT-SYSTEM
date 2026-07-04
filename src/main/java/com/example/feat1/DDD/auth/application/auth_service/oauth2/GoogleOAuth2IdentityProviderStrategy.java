package com.example.feat1.DDD.auth.application.auth_service.oauth2;

import com.example.feat1.DDD.auth.application.auth_service.google.GoogleIdTokenVerifier;
import com.example.feat1.DDD.auth.application.auth_service.google.GoogleUserInfo;
import com.example.feat1.DDD.identity_context.domain.model.enums.AuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleOAuth2IdentityProviderStrategy implements OAuth2IdentityProviderStrategy {
  private final GoogleIdTokenVerifier googleIdTokenVerifier;

  @Override
  public AuthProvider provider() {
    return AuthProvider.GOOGLE;
  }

  @Override
  public OAuth2UserInfo verify(String token) {
    GoogleUserInfo googleUserInfo = googleIdTokenVerifier.verify(token);
    return new OAuth2UserInfo(
        googleUserInfo.subject(),
        googleUserInfo.email(),
        googleUserInfo.emailVerified(),
        googleUserInfo.name(),
        googleUserInfo.hostedDomain());
  }

  @Override
  public boolean canAutoLink(OAuth2UserInfo userInfo) {
    String email = userInfo.email().toLowerCase();
    return email.endsWith("@gmail.com")
        || (userInfo.hostedDomain() != null && !userInfo.hostedDomain().isBlank());
  }
}
