package com.example.feat1.DDD.auth.application.auth_service.auth_provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.auth.TokenSerivce;
import com.example.feat1.DDD.auth.application.auth_service.google.GoogleIdTokenVerifier;
import com.example.feat1.DDD.auth.application.auth_service.google.GoogleUserInfo;
import com.example.feat1.DDD.auth.application.auth_service.oauth2.GoogleOAuth2IdentityProviderStrategy;
import com.example.feat1.DDD.auth.application.auth_service.oauth2.OAuth2AuthenticationStrategy;
import com.example.feat1.DDD.auth.application.dto.AuthRequest;
import com.example.feat1.DDD.auth.application.dto.AuthResponse;
import com.example.feat1.DDD.auth.domain.model.AuthType;
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

class GoogleAuthProviderTest {
  private GoogleIdTokenVerifier googleIdTokenVerifier;
  private ICredentialDomainRepository credentialDomainRepository;
  private IUserDomainRepository userDomainRepository;
  private IRoleDomainRepository roleDomainRepository;
  private UserDomainService userDomainService;
  private TokenSerivce tokenSerivce;
  private GoogleAuthProvider googleAuthProvider;

  @BeforeEach
  void setUp() {
    googleIdTokenVerifier = mock(GoogleIdTokenVerifier.class);
    credentialDomainRepository = mock(ICredentialDomainRepository.class);
    userDomainRepository = mock(IUserDomainRepository.class);
    roleDomainRepository = mock(IRoleDomainRepository.class);
    userDomainService = new UserDomainService();
    tokenSerivce = mock(TokenSerivce.class);
    OAuth2AuthenticationStrategy oauth2AuthenticationStrategy =
        new OAuth2AuthenticationStrategy(
            credentialDomainRepository,
            userDomainRepository,
            roleDomainRepository,
            userDomainService,
            tokenSerivce);
    GoogleOAuth2IdentityProviderStrategy googleOAuth2IdentityProviderStrategy =
        new GoogleOAuth2IdentityProviderStrategy(googleIdTokenVerifier);
    googleAuthProvider =
        new GoogleAuthProvider(oauth2AuthenticationStrategy, googleOAuth2IdentityProviderStrategy);
  }

  @Test
  void validGoogleTokenCreatesUserCredentialAndReturnsBackendTokenPair() {
    AuthResponse authResponse = new AuthResponse("access", "refresh", "Bearer", 900_000L, 60_000L);
    GoogleUserInfo googleUserInfo =
        new GoogleUserInfo("google-sub", "new@gmail.com", true, "Google User", null);
    Role userRole = new Role(UUID.randomUUID(), "USER");
    when(googleIdTokenVerifier.verify("id-token")).thenReturn(googleUserInfo);
    when(credentialDomainRepository.findByProviderAndProviderUserId(
            AuthProvider.GOOGLE, "google-sub"))
        .thenReturn(Optional.empty());
    when(userDomainRepository.findByEmailWithRoles("new@gmail.com")).thenReturn(Optional.empty());
    when(roleDomainRepository.findByName("USER")).thenReturn(Optional.of(userRole));
    when(tokenSerivce.generateAccessToken(any(User.class))).thenReturn(authResponse);

    AuthResponse response = googleAuthProvider.authenticate(googleRequest("id-token"));

    assertThat(response).isEqualTo(authResponse);
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userDomainRepository).save(userCaptor.capture());
    assertThat(userCaptor.getValue().getName()).isEqualTo("Google User");
    assertThat(userCaptor.getValue().getEmail()).isEqualTo("new@gmail.com");
    assertThat(userCaptor.getValue().getRoles()).extracting(Role::getName).containsExactly("USER");

    ArgumentCaptor<Credential> credentialCaptor = ArgumentCaptor.forClass(Credential.class);
    verify(credentialDomainRepository).save(credentialCaptor.capture());
    assertThat(credentialCaptor.getValue().getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(credentialCaptor.getValue().getProviderUserId()).isEqualTo("google-sub");
    assertThat(credentialCaptor.getValue().getPasswordHash()).isNull();
  }

  @Test
  void existingGoogleCredentialLogsInMappedUser() {
    UUID userId = UUID.randomUUID();
    User user = User.register("Existing", "existing@gmail.com");
    AuthResponse authResponse = new AuthResponse("access", "refresh", "Bearer", 900_000L, 60_000L);
    when(googleIdTokenVerifier.verify("id-token"))
        .thenReturn(new GoogleUserInfo("google-sub", "existing@gmail.com", true, "Existing", null));
    when(credentialDomainRepository.findByProviderAndProviderUserId(
            AuthProvider.GOOGLE, "google-sub"))
        .thenReturn(Optional.of(Credential.createOAuth(userId, AuthProvider.GOOGLE, "google-sub")));
    when(userDomainRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(user));
    when(tokenSerivce.generateAccessToken(user)).thenReturn(authResponse);

    AuthResponse response = googleAuthProvider.authenticate(googleRequest("id-token"));

    assertThat(response).isEqualTo(authResponse);
    verify(userDomainRepository, never()).save(any(User.class));
    verify(credentialDomainRepository, never()).save(any(Credential.class));
  }

  @Test
  void verifiedGmailAddressLinksExistingLocalUser() {
    User user = User.register("Local", "local@gmail.com");
    AuthResponse authResponse = new AuthResponse("access", "refresh", "Bearer", 900_000L, 60_000L);
    when(googleIdTokenVerifier.verify("id-token"))
        .thenReturn(new GoogleUserInfo("google-sub", "local@gmail.com", true, "Local", null));
    when(credentialDomainRepository.findByProviderAndProviderUserId(
            AuthProvider.GOOGLE, "google-sub"))
        .thenReturn(Optional.empty());
    when(userDomainRepository.findByEmailWithRoles("local@gmail.com"))
        .thenReturn(Optional.of(user));
    when(tokenSerivce.generateAccessToken(user)).thenReturn(authResponse);

    AuthResponse response = googleAuthProvider.authenticate(googleRequest("id-token"));

    assertThat(response).isEqualTo(authResponse);
    ArgumentCaptor<Credential> credentialCaptor = ArgumentCaptor.forClass(Credential.class);
    verify(credentialDomainRepository).save(credentialCaptor.capture());
    assertThat(credentialCaptor.getValue().getUserId()).isEqualTo(user.getId());
    assertThat(credentialCaptor.getValue().getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
  }

  @Test
  void nonAuthoritativeExistingEmailIsNotAutoLinked() {
    User user = User.register("Local", "local@example.com");
    when(googleIdTokenVerifier.verify("id-token"))
        .thenReturn(new GoogleUserInfo("google-sub", "local@example.com", true, "Local", null));
    when(credentialDomainRepository.findByProviderAndProviderUserId(
            AuthProvider.GOOGLE, "google-sub"))
        .thenReturn(Optional.empty());
    when(userDomainRepository.findByEmailWithRoles("local@example.com"))
        .thenReturn(Optional.of(user));

    assertThatThrownBy(() -> googleAuthProvider.authenticate(googleRequest("id-token")))
        .isInstanceOf(AppException.class)
        .extracting("code")
        .isEqualTo("GOOGLE_EMAIL_NOT_LINKABLE");

    verify(credentialDomainRepository, never()).save(any(Credential.class));
  }

  @Test
  void missingOrUnverifiedTokenReturnsStableErrors() {
    assertThatThrownBy(() -> googleAuthProvider.authenticate(googleRequest(" ")))
        .isInstanceOf(AppException.class)
        .extracting("code")
        .isEqualTo("GOOGLE_TOKEN_REQUIRED");

    when(googleIdTokenVerifier.verify("invalid"))
        .thenThrow(
            new AppException(
                "GOOGLE_TOKEN_INVALID",
                "Google token is invalid",
                org.springframework.http.HttpStatus.UNAUTHORIZED));
    assertThatThrownBy(() -> googleAuthProvider.authenticate(googleRequest("invalid")))
        .isInstanceOf(AppException.class)
        .extracting("code")
        .isEqualTo("GOOGLE_TOKEN_INVALID");

    when(googleIdTokenVerifier.verify("unverified"))
        .thenReturn(new GoogleUserInfo("google-sub", "user@gmail.com", false, "User", null));
    assertThatThrownBy(() -> googleAuthProvider.authenticate(googleRequest("unverified")))
        .isInstanceOf(AppException.class)
        .extracting("code")
        .isEqualTo("GOOGLE_EMAIL_UNVERIFIED");
  }

  private AuthRequest googleRequest(String idToken) {
    return new AuthRequest(AuthType.GOOGLE, null, null, idToken);
  }
}
