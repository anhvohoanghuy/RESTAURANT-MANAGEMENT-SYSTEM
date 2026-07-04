package com.example.feat1.DDD.auth.application.auth_service.auth_provider;

import com.example.feat1.DDD.auth.TokenSerivce;
import com.example.feat1.DDD.auth.application.dto.AuthRequest;
import com.example.feat1.DDD.auth.application.dto.AuthResponse;
import com.example.feat1.DDD.identity_context.domain.model.aggregate.User;
import com.example.feat1.DDD.identity_context.domain.model.entity.Credential;
import com.example.feat1.DDD.identity_context.domain.model.enums.AuthProvider;
import com.example.feat1.DDD.identity_context.domain.repository.credential.ICredentialDomainRepository;
import com.example.feat1.DDD.identity_context.domain.repository.user.IUserDomainRepository;
import com.example.feat1.common.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component("LOCAL")
@RequiredArgsConstructor
public class LocalAuthProvider implements IAuthProvider {
  private final ICredentialDomainRepository credentialDomainRepository;
  private final IUserDomainRepository userDomainRepository;
  private final PasswordEncoder passwordEncoder;
  private final TokenSerivce tokenSerivce;

  @Override
  public AuthResponse authenticate(AuthRequest authRequest) {
    Credential credential =
        credentialDomainRepository
            .findByProviderAndProviderUserId(AuthProvider.LOCAL, authRequest.getUsername())
            .orElseThrow(() -> invalidCredentials());

    if (!passwordEncoder.matches(authRequest.getPassword(), credential.getPasswordHash())) {
      throw invalidCredentials();
    }

    User user =
        userDomainRepository
            .findByIdWithRoles(credential.getUserId())
            .orElseThrow(
                () ->
                    new AppException("USER_NOT_FOUND", "User not found", HttpStatus.UNAUTHORIZED));

    return tokenSerivce.generateAccessToken(user);
  }

  private AppException invalidCredentials() {
    return new AppException(
        "INVALID_CREDENTIALS", "Invalid username or password", HttpStatus.UNAUTHORIZED);
  }
}
