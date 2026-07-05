package com.example.feat1.DDD.auth.application.auth_service.auth_provider;

import com.example.feat1.DDD.auth.application.auth_service.oauth2.GoogleOAuth2IdentityProviderStrategy;
import com.example.feat1.DDD.auth.application.auth_service.oauth2.OAuth2AuthenticationStrategy;
import com.example.feat1.DDD.auth.application.dto.AuthRequest;
import com.example.feat1.DDD.auth.application.dto.AuthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("GOOGLE")
@RequiredArgsConstructor
public class GoogleAuthProvider implements IAuthProvider {
  private final OAuth2AuthenticationStrategy oauth2AuthenticationStrategy;
  private final GoogleOAuth2IdentityProviderStrategy googleOAuth2IdentityProviderStrategy;

  @Override
  public AuthResponse authenticate(AuthRequest authRequest) {
    return oauth2AuthenticationStrategy.authenticate(
        googleOAuth2IdentityProviderStrategy,
        authRequest == null ? null : authRequest.getOathToken(),
        authRequest == null ? null : authRequest.getMetadata());
  }
}
