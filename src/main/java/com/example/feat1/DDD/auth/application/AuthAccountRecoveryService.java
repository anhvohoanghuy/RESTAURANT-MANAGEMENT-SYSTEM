package com.example.feat1.DDD.auth.application;

import com.example.feat1.DDD.auth.TokenSerivce;
import com.example.feat1.DDD.auth.application.notification.EmailNotificationPort;
import com.example.feat1.DDD.auth.domain.model.AuthActionTokenPurpose;
import com.example.feat1.DDD.auth.infrastructure.entity.AuthActionTokenEntity;
import com.example.feat1.DDD.auth.infrastructure.repository.IAuthActionTokenRepository;
import com.example.feat1.DDD.identity_context.domain.model.entity.Credential;
import com.example.feat1.DDD.identity_context.domain.model.enums.AuthProvider;
import com.example.feat1.DDD.identity_context.domain.repository.credential.ICredentialDomainRepository;
import com.example.feat1.DDD.identity_context.infastructure.entity.UserEntity;
import com.example.feat1.DDD.identity_context.infastructure.mapper.UserMapper;
import com.example.feat1.DDD.identity_context.infastructure.repository.IUserRepository;
import com.example.feat1.common.exception.AppException;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthAccountRecoveryService {
  private static final int TOKEN_BYTES = 32;

  private final IUserRepository userRepository;
  private final IAuthActionTokenRepository actionTokenRepository;
  private final ICredentialDomainRepository credentialDomainRepository;
  private final EmailNotificationPort emailNotificationPort;
  private final PasswordEncoder passwordEncoder;
  private final TokenSerivce tokenSerivce;
  private final EntityManager entityManager;
  private final SecureRandom secureRandom = new SecureRandom();

  @Value("${auth.action-token.email-verification-expiration:86400000}")
  private long emailVerificationExpirationMillis;

  @Value("${auth.action-token.password-reset-expiration:900000}")
  private long passwordResetExpirationMillis;

  @Transactional
  public void requestEmailVerification(String email) {
    if (email == null || email.isBlank()) {
      return;
    }

    userRepository
        .findByEmail(email)
        .filter(user -> !user.isEmailVerified())
        .ifPresent(user -> issueEmailVerification(user, Instant.now()));
  }

  @Transactional
  public void verifyEmail(String token) {
    AuthActionTokenEntity tokenEntity =
        requireUsableToken(
            token,
            AuthActionTokenPurpose.EMAIL_VERIFICATION,
            "EMAIL_VERIFICATION_TOKEN_INVALID",
            "Email verification token is invalid",
            "EMAIL_VERIFICATION_TOKEN_EXPIRED",
            "Email verification token is expired");

    Instant now = Instant.now();
    UserEntity user = tokenEntity.getUser();
    user.setEmailVerified(true);
    user.setEmailVerifiedAt(now);
    userRepository.save(user);
    tokenEntity.consume(now);
    actionTokenRepository.save(tokenEntity);
  }

  @Transactional
  public void requestPasswordReset(String email) {
    if (email == null || email.isBlank()) {
      return;
    }

    userRepository
        .findByEmail(email)
        .filter(
            user ->
                credentialDomainRepository
                    .findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL)
                    .isPresent())
        .ifPresent(user -> issuePasswordReset(user, Instant.now()));
  }

  @Transactional
  public void resetPassword(String token, String newPassword) {
    AuthActionTokenEntity tokenEntity =
        requireUsableToken(
            token,
            AuthActionTokenPurpose.PASSWORD_RESET,
            "PASSWORD_RESET_TOKEN_INVALID",
            "Password reset token is invalid",
            "PASSWORD_RESET_TOKEN_EXPIRED",
            "Password reset token is expired");

    if (newPassword == null || newPassword.isBlank() || newPassword.length() < 8) {
      throw new AppException(
          "PASSWORD_RESET_PASSWORD_INVALID",
          "Password reset password is invalid",
          HttpStatus.BAD_REQUEST);
    }

    UserEntity user = tokenEntity.getUser();
    Credential credential =
        credentialDomainRepository
            .findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL)
            .orElseThrow(
                () ->
                    new AppException(
                        "PASSWORD_RESET_TOKEN_INVALID",
                        "Password reset token is invalid",
                        HttpStatus.UNAUTHORIZED));

    credential.changePassword(passwordEncoder.encode(newPassword));
    credentialDomainRepository.save(credential);

    Instant now = Instant.now();
    tokenEntity.consume(now);
    actionTokenRepository.save(tokenEntity);
    tokenSerivce.revokeAllRefreshTokens(user.getId());
  }

  private void issueEmailVerification(UserEntity user, Instant now) {
    String token = issueToken(user, AuthActionTokenPurpose.EMAIL_VERIFICATION, now);
    emailNotificationPort.sendEmailVerification(UserMapper.userToDomain(user).orElseThrow(), token);
  }

  private void issuePasswordReset(UserEntity user, Instant now) {
    String token = issueToken(user, AuthActionTokenPurpose.PASSWORD_RESET, now);
    emailNotificationPort.sendPasswordReset(UserMapper.userToDomain(user).orElseThrow(), token);
  }

  private String issueToken(UserEntity user, AuthActionTokenPurpose purpose, Instant now) {
    String token = generateRawToken();
    long expirationMillis =
        purpose == AuthActionTokenPurpose.EMAIL_VERIFICATION
            ? emailVerificationExpirationMillis
            : passwordResetExpirationMillis;
    UserEntity userReference = entityManager.getReference(UserEntity.class, user.getId());
    actionTokenRepository.save(
        AuthActionTokenEntity.create(
            userReference, hash(token), purpose, now.plusMillis(expirationMillis), now));
    return token;
  }

  private AuthActionTokenEntity requireUsableToken(
      String token,
      AuthActionTokenPurpose purpose,
      String invalidCode,
      String invalidMessage,
      String expiredCode,
      String expiredMessage) {
    if (token == null || token.isBlank()) {
      throw new AppException(invalidCode, invalidMessage, HttpStatus.UNAUTHORIZED);
    }

    AuthActionTokenEntity tokenEntity =
        actionTokenRepository
            .findByTokenHashAndPurpose(hash(token), purpose)
            .orElseThrow(
                () -> new AppException(invalidCode, invalidMessage, HttpStatus.UNAUTHORIZED));

    if (tokenEntity.isConsumed()) {
      throw new AppException(invalidCode, invalidMessage, HttpStatus.UNAUTHORIZED);
    }
    if (tokenEntity.isExpired(Instant.now())) {
      throw new AppException(expiredCode, expiredMessage, HttpStatus.UNAUTHORIZED);
    }
    return tokenEntity;
  }

  private String generateRawToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hash(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
