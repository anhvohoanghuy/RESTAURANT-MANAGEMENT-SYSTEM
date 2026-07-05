package com.example.feat1.DDD.auth.application.auth_service.oauth2;

import com.example.feat1.DDD.auth.TokenSerivce;
import com.example.feat1.DDD.auth.application.dto.AuthRequestMetadata;
import com.example.feat1.DDD.auth.application.dto.AuthResponse;
import com.example.feat1.DDD.identity_context.application.dto.RoleEnum;
import com.example.feat1.DDD.identity_context.domain.model.aggregate.User;
import com.example.feat1.DDD.identity_context.domain.model.entity.Credential;
import com.example.feat1.DDD.identity_context.domain.model.entity.Role;
import com.example.feat1.DDD.identity_context.domain.repository.credential.ICredentialDomainRepository;
import com.example.feat1.DDD.identity_context.domain.repository.role.IRoleDomainRepository;
import com.example.feat1.DDD.identity_context.domain.repository.user.IUserDomainRepository;
import com.example.feat1.DDD.identity_context.domain.service.UserDomainService;
import com.example.feat1.common.exception.AppException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OAuth2AuthenticationStrategy {
  private final ICredentialDomainRepository credentialDomainRepository;
  private final IUserDomainRepository userDomainRepository;
  private final IRoleDomainRepository roleDomainRepository;
  private final UserDomainService userDomainService;
  private final TokenSerivce tokenSerivce;

  @Transactional
  public AuthResponse authenticate(OAuth2IdentityProviderStrategy providerStrategy, String token) {
    return authenticate(providerStrategy, token, AuthRequestMetadata.empty());
  }

  @Transactional
  public AuthResponse authenticate(
      OAuth2IdentityProviderStrategy providerStrategy, String token, AuthRequestMetadata metadata) {
    if (token == null || token.isBlank()) {
      throw new AppException(
          providerStrategy.tokenRequiredCode(),
          providerStrategy.tokenRequiredMessage(),
          providerStrategy.tokenRequiredStatus());
    }

    OAuth2UserInfo userInfo = providerStrategy.verify(token);
    if (!userInfo.emailVerified()) {
      throw new AppException(
          providerStrategy.emailUnverifiedCode(),
          providerStrategy.emailUnverifiedMessage(),
          providerStrategy.emailUnverifiedStatus());
    }

    return credentialDomainRepository
        .findByProviderAndProviderUserId(providerStrategy.provider(), userInfo.providerUserId())
        .map(credential -> loginExistingCredential(credential.getUserId(), metadata))
        .orElseGet(() -> createOrLinkUser(providerStrategy, userInfo, metadata));
  }

  private AuthResponse loginExistingCredential(UUID userId, AuthRequestMetadata metadata) {
    User user =
        userDomainRepository
            .findByIdWithRoles(userId)
            .orElseThrow(
                () ->
                    new AppException("USER_NOT_FOUND", "User not found", HttpStatus.UNAUTHORIZED));
    return tokenSerivce.generateAccessToken(user, metadata);
  }

  private AuthResponse createOrLinkUser(
      OAuth2IdentityProviderStrategy providerStrategy,
      OAuth2UserInfo userInfo,
      AuthRequestMetadata metadata) {
    return userDomainRepository
        .findByEmailWithRoles(userInfo.email())
        .map(user -> linkExistingUser(providerStrategy, user, userInfo, metadata))
        .orElseGet(() -> registerUser(providerStrategy, userInfo, metadata));
  }

  private AuthResponse linkExistingUser(
      OAuth2IdentityProviderStrategy providerStrategy,
      User user,
      OAuth2UserInfo userInfo,
      AuthRequestMetadata metadata) {
    if (!providerStrategy.canAutoLink(userInfo)) {
      throw new AppException(
          providerStrategy.emailNotLinkableCode(),
          providerStrategy.emailNotLinkableMessage(),
          providerStrategy.emailNotLinkableStatus());
    }

    if (markEmailVerifiedIfProviderVerified(user, userInfo)) {
      userDomainRepository.save(user);
    }
    credentialDomainRepository.save(
        Credential.createOAuth(
            user.getId(), providerStrategy.provider(), userInfo.providerUserId()));
    return tokenSerivce.generateAccessToken(user, metadata);
  }

  private AuthResponse registerUser(
      OAuth2IdentityProviderStrategy providerStrategy,
      OAuth2UserInfo userInfo,
      AuthRequestMetadata metadata) {
    User user = User.register(displayName(userInfo), userInfo.email());
    markEmailVerifiedIfProviderVerified(user, userInfo);
    userDomainService.validateUser(user);
    Role userRole =
        roleDomainRepository
            .findByName(RoleEnum.USER.getName())
            .orElseThrow(() -> new IllegalStateException("Role seed missing: USER"));
    user.assignRole(userRole);
    userDomainRepository.save(user);
    credentialDomainRepository.save(
        Credential.createOAuth(
            user.getId(), providerStrategy.provider(), userInfo.providerUserId()));
    return tokenSerivce.generateAccessToken(user, metadata);
  }

  private boolean markEmailVerifiedIfProviderVerified(User user, OAuth2UserInfo userInfo) {
    if (userInfo.emailVerified() && !user.isEmailVerified()) {
      user.markEmailVerified(Instant.now());
      return true;
    }
    return false;
  }

  private String displayName(OAuth2UserInfo userInfo) {
    if (userInfo.displayName() != null && !userInfo.displayName().isBlank()) {
      return userInfo.displayName();
    }
    int atIndex = userInfo.email().indexOf('@');
    if (atIndex > 0) {
      return userInfo.email().substring(0, atIndex);
    }
    return userInfo.email();
  }
}
