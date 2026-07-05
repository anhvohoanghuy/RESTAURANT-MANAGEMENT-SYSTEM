package com.example.feat1.DDD.auth.infrastructure.presentation;

import com.example.feat1.DDD.auth.application.AuthAccountRecoveryService;
import com.example.feat1.DDD.auth.application.AuthRateLimitService;
import com.example.feat1.DDD.auth.application.AuthService;
import com.example.feat1.DDD.auth.application.AuthSessionService;
import com.example.feat1.DDD.auth.application.LoginLockoutService;
import com.example.feat1.DDD.auth.application.SecurityAuditService;
import com.example.feat1.DDD.auth.application.dto.AuthRequest;
import com.example.feat1.DDD.auth.application.dto.AuthRequestMetadata;
import com.example.feat1.DDD.auth.application.dto.AuthResponse;
import com.example.feat1.DDD.auth.application.dto.AuthSessionResponse;
import com.example.feat1.DDD.auth.application.dto.EmailRequest;
import com.example.feat1.DDD.auth.application.dto.GoogleLoginRequest;
import com.example.feat1.DDD.auth.application.dto.LoginRequest;
import com.example.feat1.DDD.auth.application.dto.PasswordResetRequest;
import com.example.feat1.DDD.auth.application.dto.RefreshTokenRequest;
import com.example.feat1.DDD.auth.application.dto.RegisterLocalRequest;
import com.example.feat1.DDD.auth.application.dto.RevokeOtherSessionsRequest;
import com.example.feat1.DDD.auth.application.dto.TokenRequest;
import com.example.feat1.DDD.auth.domain.model.AuthType;
import com.example.feat1.DDD.auth.domain.model.SecurityEventOutcome;
import com.example.feat1.DDD.auth.domain.model.SecurityEventType;
import com.example.feat1.DDD.auth.infrastructure.security.CustomUserDetails;
import com.example.feat1.DDD.identity_context.application.dto.RegisterRequestDto;
import com.example.feat1.DDD.identity_context.application.dto.RoleEnum;
import com.example.feat1.DDD.identity_context.application.usecase.RegisterUserUseCase;
import com.example.feat1.DDD.identity_context.domain.model.enums.AuthProvider;
import com.example.feat1.common.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthService authService;
  private final RegisterUserUseCase registerUserUseCase;
  private final AuthAccountRecoveryService authAccountRecoveryService;
  private final AuthRateLimitService authRateLimitService;
  private final LoginLockoutService loginLockoutService;
  private final SecurityAuditService securityAuditService;
  private final AuthSessionService authSessionService;

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(
      @RequestBody RegisterLocalRequest request, HttpServletRequest servletRequest) {
    AuthRequestMetadata metadata = metadata(servletRequest);
    RegisterRequestDto registerRequest = new RegisterRequestDto();
    registerRequest.setUsername(request.username());
    registerRequest.setEmail(request.email());
    registerRequest.setPassword(request.password());
    registerRequest.setProviderUserId(request.username());
    registerRequest.setLoginType(AuthProvider.LOCAL);
    registerRequest.setRoles(List.of(RoleEnum.USER.getName()));

    registerUserUseCase.execute(registerRequest);
    authAccountRecoveryService.requestEmailVerification(request.email());
    AuthResponse response =
        authService.login(toLocalAuthRequest(request.username(), request.password(), metadata));
    audit(
        SecurityEventType.LOGIN,
        SecurityEventOutcome.SUCCESS,
        null,
        request.username(),
        metadata,
        "REGISTER_AUTO_LOGIN");
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(
      @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    AuthRequestMetadata metadata = metadata(servletRequest);
    String username = request == null ? null : request.username();
    authRateLimitService.checkLogin(username, metadata);
    try {
      loginLockoutService.checkNotLocked(username);
      AuthResponse response =
          authService.login(
              toLocalAuthRequest(username, request == null ? null : request.password(), metadata));
      loginLockoutService.recordSuccess(username);
      audit(SecurityEventType.LOGIN, SecurityEventOutcome.SUCCESS, null, username, metadata, null);
      return ResponseEntity.ok(response);
    } catch (AppException exception) {
      auditLoginFailure(username, metadata, exception);
      throw exception;
    }
  }

  @PostMapping("/google")
  public ResponseEntity<AuthResponse> google(
      @RequestBody GoogleLoginRequest request, HttpServletRequest servletRequest) {
    AuthRequestMetadata metadata = metadata(servletRequest);
    authRateLimitService.checkGoogle(metadata);
    try {
      AuthResponse response = authService.login(toGoogleAuthRequest(request, metadata));
      audit(
          SecurityEventType.GOOGLE_LOGIN, SecurityEventOutcome.SUCCESS, null, null, metadata, null);
      return ResponseEntity.ok(response);
    } catch (AppException exception) {
      audit(
          SecurityEventType.GOOGLE_LOGIN,
          SecurityEventOutcome.FAILURE,
          null,
          null,
          metadata,
          exception.getCode());
      throw exception;
    }
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(
      @RequestBody RefreshTokenRequest request, HttpServletRequest servletRequest) {
    AuthRequestMetadata metadata = metadata(servletRequest);
    try {
      AuthResponse response = authService.refreshToken(requiredRefreshToken(request), metadata);
      audit(SecurityEventType.REFRESH, SecurityEventOutcome.SUCCESS, null, null, metadata, null);
      return ResponseEntity.ok(response);
    } catch (AppException exception) {
      SecurityEventType eventType =
          "REFRESH_TOKEN_REUSED".equals(exception.getCode())
              ? SecurityEventType.REFRESH_REUSE
              : SecurityEventType.REFRESH;
      audit(eventType, SecurityEventOutcome.FAILURE, null, null, metadata, exception.getCode());
      throw exception;
    }
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @RequestBody RefreshTokenRequest request, HttpServletRequest servletRequest) {
    AuthRequestMetadata metadata = metadata(servletRequest);
    authService.logout(requiredRefreshToken(request));
    audit(SecurityEventType.LOGOUT, SecurityEventOutcome.SUCCESS, null, null, metadata, null);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/email/verification/request")
  public ResponseEntity<Void> requestEmailVerification(
      @RequestBody EmailRequest request, HttpServletRequest servletRequest) {
    AuthRequestMetadata metadata = metadata(servletRequest);
    authRateLimitService.checkRecovery(request == null ? null : request.email(), metadata);
    authAccountRecoveryService.requestEmailVerification(request == null ? null : request.email());
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/email/verify")
  public ResponseEntity<Void> verifyEmail(
      @RequestBody TokenRequest request, HttpServletRequest servletRequest) {
    AuthRequestMetadata metadata = metadata(servletRequest);
    try {
      authAccountRecoveryService.verifyEmail(request == null ? null : request.token());
      audit(
          SecurityEventType.EMAIL_VERIFY, SecurityEventOutcome.SUCCESS, null, null, metadata, null);
    } catch (AppException exception) {
      audit(
          SecurityEventType.EMAIL_VERIFY,
          SecurityEventOutcome.FAILURE,
          null,
          null,
          metadata,
          exception.getCode());
      throw exception;
    }
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/password/forgot")
  public ResponseEntity<Void> forgotPassword(
      @RequestBody EmailRequest request, HttpServletRequest servletRequest) {
    AuthRequestMetadata metadata = metadata(servletRequest);
    authRateLimitService.checkRecovery(request == null ? null : request.email(), metadata);
    authAccountRecoveryService.requestPasswordReset(request == null ? null : request.email());
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/password/reset")
  public ResponseEntity<Void> resetPassword(
      @RequestBody PasswordResetRequest request, HttpServletRequest servletRequest) {
    AuthRequestMetadata metadata = metadata(servletRequest);
    try {
      authAccountRecoveryService.resetPassword(
          request == null ? null : request.token(), request == null ? null : request.newPassword());
      audit(
          SecurityEventType.PASSWORD_RESET,
          SecurityEventOutcome.SUCCESS,
          null,
          null,
          metadata,
          null);
    } catch (AppException exception) {
      audit(
          SecurityEventType.PASSWORD_RESET,
          SecurityEventOutcome.FAILURE,
          null,
          null,
          metadata,
          exception.getCode());
      throw exception;
    }
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/sessions")
  public ResponseEntity<List<AuthSessionResponse>> sessions(
      @AuthenticationPrincipal CustomUserDetails principal) {
    return ResponseEntity.ok(authSessionService.listActiveSessions(requiredUserId(principal)));
  }

  @DeleteMapping("/sessions/{sessionId}")
  public ResponseEntity<Void> revokeSession(
      @PathVariable UUID sessionId,
      @AuthenticationPrincipal CustomUserDetails principal,
      HttpServletRequest servletRequest) {
    AuthRequestMetadata metadata = metadata(servletRequest);
    UUID userId = requiredUserId(principal);
    authSessionService.revokeSession(userId, sessionId);
    audit(
        SecurityEventType.SESSION_REVOKE,
        SecurityEventOutcome.SUCCESS,
        userId,
        principal.getUsername(),
        metadata,
        "REVOKE_ONE");
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/sessions/revoke-others")
  public ResponseEntity<Void> revokeOtherSessions(
      @RequestBody RevokeOtherSessionsRequest request,
      @AuthenticationPrincipal CustomUserDetails principal,
      HttpServletRequest servletRequest) {
    AuthRequestMetadata metadata = metadata(servletRequest);
    UUID userId = requiredUserId(principal);
    authSessionService.revokeOtherSessions(userId, request == null ? null : request.refreshToken());
    audit(
        SecurityEventType.SESSION_REVOKE,
        SecurityEventOutcome.SUCCESS,
        userId,
        principal.getUsername(),
        metadata,
        "REVOKE_OTHERS");
    return ResponseEntity.noContent().build();
  }

  private AuthRequest toLocalAuthRequest(
      String username, String password, AuthRequestMetadata metadata) {
    return new AuthRequest(AuthType.LOCAL, username, password, null, metadata);
  }

  private AuthRequest toGoogleAuthRequest(
      GoogleLoginRequest request, AuthRequestMetadata metadata) {
    return new AuthRequest(
        AuthType.GOOGLE, null, null, request == null ? null : request.idToken(), metadata);
  }

  private String requiredRefreshToken(RefreshTokenRequest request) {
    if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
      throw new AppException(
          "REFRESH_TOKEN_REQUIRED", "Refresh token is required", HttpStatus.BAD_REQUEST);
    }
    return request.refreshToken();
  }

  private AuthRequestMetadata metadata(HttpServletRequest request) {
    if (request == null) {
      return AuthRequestMetadata.empty();
    }
    return new AuthRequestMetadata(clientIp(request), request.getHeader(HttpHeaders.USER_AGENT));
  }

  private String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private void auditLoginFailure(
      String username, AuthRequestMetadata metadata, AppException exception) {
    if ("INVALID_CREDENTIALS".equals(exception.getCode())) {
      audit(
          SecurityEventType.LOGIN,
          SecurityEventOutcome.FAILURE,
          null,
          username,
          metadata,
          exception.getCode());
      try {
        loginLockoutService.recordFailure(username);
      } catch (AppException lockoutException) {
        audit(
            SecurityEventType.ACCOUNT_LOCKOUT,
            SecurityEventOutcome.FAILURE,
            null,
            username,
            metadata,
            lockoutException.getCode());
        throw lockoutException;
      }
      return;
    }

    SecurityEventType eventType =
        "ACCOUNT_LOCKED".equals(exception.getCode())
            ? SecurityEventType.ACCOUNT_LOCKOUT
            : SecurityEventType.LOGIN;
    audit(eventType, SecurityEventOutcome.FAILURE, null, username, metadata, exception.getCode());
  }

  private void audit(
      SecurityEventType eventType,
      SecurityEventOutcome outcome,
      UUID userId,
      String principal,
      AuthRequestMetadata metadata,
      String reason) {
    securityAuditService.record(eventType, outcome, userId, principal, metadata, reason);
  }

  private UUID requiredUserId(CustomUserDetails principal) {
    if (principal == null) {
      throw new AppException(
          "UNAUTHENTICATED", "Authentication is required", HttpStatus.UNAUTHORIZED);
    }
    return principal.getId();
  }
}
