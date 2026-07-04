package com.example.feat1.DDD.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.auth.TokenSerivce;
import com.example.feat1.DDD.auth.application.notification.EmailNotificationPort;
import com.example.feat1.DDD.auth.domain.model.AuthActionTokenPurpose;
import com.example.feat1.DDD.auth.infrastructure.entity.AuthActionTokenEntity;
import com.example.feat1.DDD.auth.infrastructure.repository.IAuthActionTokenRepository;
import com.example.feat1.DDD.identity_context.domain.model.entity.Credential;
import com.example.feat1.DDD.identity_context.domain.model.enums.AuthProvider;
import com.example.feat1.DDD.identity_context.domain.repository.credential.ICredentialDomainRepository;
import com.example.feat1.DDD.identity_context.infastructure.entity.UserEntity;
import com.example.feat1.DDD.identity_context.infastructure.repository.IUserRepository;
import com.example.feat1.common.exception.AppException;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthAccountRecoveryServiceTest {
  private IUserRepository userRepository;
  private IAuthActionTokenRepository actionTokenRepository;
  private ICredentialDomainRepository credentialDomainRepository;
  private EmailNotificationPort emailNotificationPort;
  private PasswordEncoder passwordEncoder;
  private TokenSerivce tokenSerivce;
  private EntityManager entityManager;
  private AuthAccountRecoveryService service;

  @BeforeEach
  void setUp() {
    userRepository = mock(IUserRepository.class);
    actionTokenRepository = mock(IAuthActionTokenRepository.class);
    credentialDomainRepository = mock(ICredentialDomainRepository.class);
    emailNotificationPort = mock(EmailNotificationPort.class);
    passwordEncoder = mock(PasswordEncoder.class);
    tokenSerivce = mock(TokenSerivce.class);
    entityManager = mock(EntityManager.class);
    service =
        new AuthAccountRecoveryService(
            userRepository,
            actionTokenRepository,
            credentialDomainRepository,
            emailNotificationPort,
            passwordEncoder,
            tokenSerivce,
            entityManager);
    ReflectionTestUtils.setField(service, "emailVerificationExpirationMillis", 86_400_000L);
    ReflectionTestUtils.setField(service, "passwordResetExpirationMillis", 900_000L);
  }

  @Test
  void requestPasswordResetCreatesHashedSingleUseTokenAndEmitsRawTokenThroughPort() {
    UserEntity user = user("local@example.com");
    Credential credential = Credential.createLocal(user.getId(), "local", "old-hash");
    when(userRepository.findByEmail("local@example.com")).thenReturn(Optional.of(user));
    when(credentialDomainRepository.findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL))
        .thenReturn(Optional.of(credential));
    when(entityManager.getReference(UserEntity.class, user.getId())).thenReturn(user);

    service.requestPasswordReset("local@example.com");

    ArgumentCaptor<AuthActionTokenEntity> tokenCaptor =
        ArgumentCaptor.forClass(AuthActionTokenEntity.class);
    verify(actionTokenRepository).save(tokenCaptor.capture());
    ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
    verify(emailNotificationPort).sendPasswordReset(any(), rawTokenCaptor.capture());

    assertThat(tokenCaptor.getValue().getPurpose())
        .isEqualTo(AuthActionTokenPurpose.PASSWORD_RESET);
    assertThat(tokenCaptor.getValue().getTokenHash()).isNotBlank();
    assertThat(tokenCaptor.getValue().getTokenHash()).isNotEqualTo(rawTokenCaptor.getValue());
    assertThat(tokenCaptor.getValue().getConsumedAt()).isNull();
  }

  @Test
  void verifyEmailConsumesTokenAndMarksUserVerified() {
    UserEntity user = user("verify@example.com");
    AuthActionTokenEntity token =
        AuthActionTokenEntity.create(
            user,
            "hash",
            AuthActionTokenPurpose.EMAIL_VERIFICATION,
            Instant.now().plusSeconds(60),
            Instant.now());
    when(actionTokenRepository.findByTokenHashAndPurpose(
            anyString(), eq(AuthActionTokenPurpose.EMAIL_VERIFICATION)))
        .thenReturn(Optional.of(token));

    service.verifyEmail("raw-token");

    assertThat(user.isEmailVerified()).isTrue();
    assertThat(user.getEmailVerifiedAt()).isNotNull();
    assertThat(token.isConsumed()).isTrue();
    verify(userRepository).save(user);
    verify(actionTokenRepository).save(token);
  }

  @Test
  void expiredVerificationTokenReturnsStableError() {
    UserEntity user = user("expired@example.com");
    AuthActionTokenEntity token =
        AuthActionTokenEntity.create(
            user,
            "hash",
            AuthActionTokenPurpose.EMAIL_VERIFICATION,
            Instant.now().minusSeconds(1),
            Instant.now().minusSeconds(60));
    when(actionTokenRepository.findByTokenHashAndPurpose(
            anyString(), eq(AuthActionTokenPurpose.EMAIL_VERIFICATION)))
        .thenReturn(Optional.of(token));

    assertThatThrownBy(() -> service.verifyEmail("raw-token"))
        .isInstanceOf(AppException.class)
        .extracting("code")
        .isEqualTo("EMAIL_VERIFICATION_TOKEN_EXPIRED");
  }

  @Test
  void resetPasswordChangesLocalCredentialConsumesTokenAndRevokesSessions() {
    UserEntity user = user("reset@example.com");
    AuthActionTokenEntity token =
        AuthActionTokenEntity.create(
            user,
            "hash",
            AuthActionTokenPurpose.PASSWORD_RESET,
            Instant.now().plusSeconds(60),
            Instant.now());
    Credential credential = Credential.createLocal(user.getId(), "reset", "old-hash");
    when(actionTokenRepository.findByTokenHashAndPurpose(
            anyString(), eq(AuthActionTokenPurpose.PASSWORD_RESET)))
        .thenReturn(Optional.of(token));
    when(credentialDomainRepository.findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL))
        .thenReturn(Optional.of(credential));
    when(passwordEncoder.encode("new-secret")).thenReturn("new-hash");

    service.resetPassword("raw-token", "new-secret");

    assertThat(credential.getPasswordHash()).isEqualTo("new-hash");
    assertThat(token.isConsumed()).isTrue();
    verify(credentialDomainRepository).save(credential);
    verify(actionTokenRepository).save(token);
    verify(tokenSerivce).revokeAllRefreshTokens(user.getId());
  }

  @Test
  void invalidPasswordResetTokenUsesStableError() {
    when(actionTokenRepository.findByTokenHashAndPurpose(
            anyString(), eq(AuthActionTokenPurpose.PASSWORD_RESET)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.resetPassword("unknown", "new-secret"))
        .isInstanceOf(AppException.class)
        .extracting("code")
        .isEqualTo("PASSWORD_RESET_TOKEN_INVALID");
  }

  @Test
  void invalidNewPasswordUsesStableError() {
    UserEntity user = user("reset@example.com");
    AuthActionTokenEntity token =
        AuthActionTokenEntity.create(
            user,
            "hash",
            AuthActionTokenPurpose.PASSWORD_RESET,
            Instant.now().plusSeconds(60),
            Instant.now());
    when(actionTokenRepository.findByTokenHashAndPurpose(
            anyString(), eq(AuthActionTokenPurpose.PASSWORD_RESET)))
        .thenReturn(Optional.of(token));

    assertThatThrownBy(() -> service.resetPassword("raw-token", "short"))
        .isInstanceOf(AppException.class)
        .extracting("code")
        .isEqualTo("PASSWORD_RESET_PASSWORD_INVALID");
  }

  private UserEntity user(String email) {
    UserEntity user = new UserEntity();
    user.setId(UUID.randomUUID());
    user.setName(email.substring(0, email.indexOf('@')));
    user.setEmail(email);
    user.setUserRoles(new java.util.HashSet<>());
    return user;
  }
}
