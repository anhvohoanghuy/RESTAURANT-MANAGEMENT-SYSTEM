package com.example.feat1.DDD.auth.application.auth_service.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.auth.TokenSerivce;
import com.example.feat1.DDD.auth.application.dto.AuthResponse;
import com.example.feat1.DDD.identity_context.domain.model.aggregate.User;
import com.example.feat1.DDD.identity_context.domain.model.entity.Credential;
import com.example.feat1.DDD.identity_context.domain.model.entity.Role;
import com.example.feat1.DDD.identity_context.domain.model.enums.AuthProvider;
import com.example.feat1.DDD.identity_context.domain.repository.credential.ICredentialDomainRepository;
import com.example.feat1.DDD.identity_context.domain.repository.role.IRoleDomainRepository;
import com.example.feat1.DDD.identity_context.domain.repository.user.IUserDomainRepository;
import com.example.feat1.DDD.identity_context.domain.service.UserDomainService;
import com.example.feat1.common.exception.AppException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OAuth2AuthenticationStrategyTest {
  private ICredentialDomainRepository credentialDomainRepository;
  private IUserDomainRepository userDomainRepository;
  private IRoleDomainRepository roleDomainRepository;
  private TokenSerivce tokenSerivce;
  private OAuth2AuthenticationStrategy oauth2AuthenticationStrategy;
  private FakeOAuth2ProviderStrategy providerStrategy;

  @BeforeEach
  void setUp() {
    credentialDomainRepository = mock(ICredentialDomainRepository.class);
    userDomainRepository = mock(IUserDomainRepository.class);
    roleDomainRepository = mock(IRoleDomainRepository.class);
    tokenSerivce = mock(TokenSerivce.class);
    oauth2AuthenticationStrategy =
        new OAuth2AuthenticationStrategy(
            credentialDomainRepository,
            userDomainRepository,
            roleDomainRepository,
            new UserDomainService(),
            tokenSerivce);
    providerStrategy = new FakeOAuth2ProviderStrategy();
  }

  @Test
  void existingProviderCredentialLogsInMappedUser() {
    User user = User.register("OAuth User", "oauth@example.com");
    AuthResponse authResponse = new AuthResponse("access", "refresh", "Bearer", 900_000L, 60_000L);
    when(credentialDomainRepository.findByProviderAndProviderUserId(any(), any()))
        .thenReturn(
            Optional.of(Credential.createOAuth(user.getId(), AuthProvider.GITHUB, "provider-sub")));
    when(userDomainRepository.findByIdWithRoles(user.getId())).thenReturn(Optional.of(user));
    when(tokenSerivce.generateAccessToken(any(User.class), any())).thenReturn(authResponse);

    AuthResponse response = oauth2AuthenticationStrategy.authenticate(providerStrategy, "token");

    assertThat(response).isEqualTo(authResponse);
    verify(userDomainRepository, never()).save(any(User.class));
    verify(credentialDomainRepository, never()).save(any(Credential.class));
  }

  @Test
  void newOAuth2UserUsesStrategyProviderForSavedCredential() {
    AuthResponse authResponse = new AuthResponse("access", "refresh", "Bearer", 900_000L, 60_000L);
    Role userRole = new Role(UUID.randomUUID(), "USER");
    when(credentialDomainRepository.findByProviderAndProviderUserId(
            AuthProvider.GITHUB, "provider-sub"))
        .thenReturn(Optional.empty());
    when(userDomainRepository.findByEmailWithRoles("oauth@example.com"))
        .thenReturn(Optional.empty());
    when(roleDomainRepository.findByName("USER")).thenReturn(Optional.of(userRole));
    when(tokenSerivce.generateAccessToken(any(User.class), any())).thenReturn(authResponse);

    AuthResponse response = oauth2AuthenticationStrategy.authenticate(providerStrategy, "token");

    assertThat(response).isEqualTo(authResponse);
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userDomainRepository).save(userCaptor.capture());
    assertThat(userCaptor.getValue().getName()).isEqualTo("OAuth User");
    assertThat(userCaptor.getValue().getEmail()).isEqualTo("oauth@example.com");

    ArgumentCaptor<Credential> credentialCaptor = ArgumentCaptor.forClass(Credential.class);
    verify(credentialDomainRepository).save(credentialCaptor.capture());
    assertThat(credentialCaptor.getValue().getAuthProvider()).isEqualTo(AuthProvider.GITHUB);
    assertThat(credentialCaptor.getValue().getProviderUserId()).isEqualTo("provider-sub");
  }

  @Test
  void existingEmailCannotAutoLinkWhenProviderStrategyRejectsIt() {
    User user = User.register("Local", "oauth@example.com");
    providerStrategy.canAutoLink = false;
    when(credentialDomainRepository.findByProviderAndProviderUserId(
            AuthProvider.GITHUB, "provider-sub"))
        .thenReturn(Optional.empty());
    when(userDomainRepository.findByEmailWithRoles("oauth@example.com"))
        .thenReturn(Optional.of(user));

    assertThatThrownBy(() -> oauth2AuthenticationStrategy.authenticate(providerStrategy, "token"))
        .isInstanceOf(AppException.class)
        .extracting("code")
        .isEqualTo("GITHUB_EMAIL_NOT_LINKABLE");

    verify(credentialDomainRepository, never()).save(any(Credential.class));
  }

  @Test
  void missingOrUnverifiedTokenUsesProviderSpecificErrors() {
    assertThatThrownBy(() -> oauth2AuthenticationStrategy.authenticate(providerStrategy, " "))
        .isInstanceOf(AppException.class)
        .extracting("code")
        .isEqualTo("GITHUB_TOKEN_REQUIRED");

    providerStrategy.userInfo =
        new OAuth2UserInfo("provider-sub", "oauth@example.com", false, "OAuth User", null);

    assertThatThrownBy(() -> oauth2AuthenticationStrategy.authenticate(providerStrategy, "token"))
        .isInstanceOf(AppException.class)
        .extracting("code")
        .isEqualTo("GITHUB_EMAIL_UNVERIFIED");
  }

  private static class FakeOAuth2ProviderStrategy implements OAuth2IdentityProviderStrategy {
    private OAuth2UserInfo userInfo =
        new OAuth2UserInfo("provider-sub", "oauth@example.com", true, "OAuth User", null);
    private boolean canAutoLink = true;

    @Override
    public AuthProvider provider() {
      return AuthProvider.GITHUB;
    }

    @Override
    public OAuth2UserInfo verify(String token) {
      return userInfo;
    }

    @Override
    public boolean canAutoLink(OAuth2UserInfo userInfo) {
      return canAutoLink;
    }
  }
}
