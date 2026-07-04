package com.example.feat1.DDD.auth.infrastructure.presentation;

import com.example.feat1.DDD.auth.application.AuthAccountRecoveryService;
import com.example.feat1.DDD.auth.application.AuthService;
import com.example.feat1.DDD.auth.application.dto.AuthRequest;
import com.example.feat1.DDD.auth.application.dto.AuthResponse;
import com.example.feat1.DDD.auth.application.dto.EmailRequest;
import com.example.feat1.DDD.auth.application.dto.GoogleLoginRequest;
import com.example.feat1.DDD.auth.application.dto.LoginRequest;
import com.example.feat1.DDD.auth.application.dto.PasswordResetRequest;
import com.example.feat1.DDD.auth.application.dto.RefreshTokenRequest;
import com.example.feat1.DDD.auth.application.dto.RegisterLocalRequest;
import com.example.feat1.DDD.auth.application.dto.TokenRequest;
import com.example.feat1.DDD.auth.domain.model.AuthType;
import com.example.feat1.DDD.identity_context.application.dto.RegisterRequestDto;
import com.example.feat1.DDD.identity_context.application.dto.RoleEnum;
import com.example.feat1.DDD.identity_context.application.usecase.RegisterUserUseCase;
import com.example.feat1.DDD.identity_context.domain.model.enums.AuthProvider;
import com.example.feat1.common.exception.AppException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@RequestBody RegisterLocalRequest request) {
    RegisterRequestDto registerRequest = new RegisterRequestDto();
    registerRequest.setUsername(request.username());
    registerRequest.setEmail(request.email());
    registerRequest.setPassword(request.password());
    registerRequest.setProviderUserId(request.username());
    registerRequest.setLoginType(AuthProvider.LOCAL);
    registerRequest.setRoles(List.of(RoleEnum.USER.getName()));

    registerUserUseCase.execute(registerRequest);
    authAccountRecoveryService.requestEmailVerification(request.email());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(authService.login(toLocalAuthRequest(request.username(), request.password())));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
    return ResponseEntity.ok(
        authService.login(toLocalAuthRequest(request.username(), request.password())));
  }

  @PostMapping("/google")
  public ResponseEntity<AuthResponse> google(@RequestBody GoogleLoginRequest request) {
    return ResponseEntity.ok(authService.login(toGoogleAuthRequest(request)));
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshTokenRequest request) {
    return ResponseEntity.ok(authService.refreshToken(requiredRefreshToken(request)));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest request) {
    authService.logout(requiredRefreshToken(request));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/email/verification/request")
  public ResponseEntity<Void> requestEmailVerification(@RequestBody EmailRequest request) {
    authAccountRecoveryService.requestEmailVerification(request == null ? null : request.email());
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/email/verify")
  public ResponseEntity<Void> verifyEmail(@RequestBody TokenRequest request) {
    authAccountRecoveryService.verifyEmail(request == null ? null : request.token());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/password/forgot")
  public ResponseEntity<Void> forgotPassword(@RequestBody EmailRequest request) {
    authAccountRecoveryService.requestPasswordReset(request == null ? null : request.email());
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/password/reset")
  public ResponseEntity<Void> resetPassword(@RequestBody PasswordResetRequest request) {
    authAccountRecoveryService.resetPassword(
        request == null ? null : request.token(), request == null ? null : request.newPassword());
    return ResponseEntity.noContent().build();
  }

  private AuthRequest toLocalAuthRequest(String username, String password) {
    return new AuthRequest(AuthType.LOCAL, username, password, null);
  }

  private AuthRequest toGoogleAuthRequest(GoogleLoginRequest request) {
    return new AuthRequest(AuthType.GOOGLE, null, null, request == null ? null : request.idToken());
  }

  private String requiredRefreshToken(RefreshTokenRequest request) {
    if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
      throw new AppException(
          "REFRESH_TOKEN_REQUIRED", "Refresh token is required", HttpStatus.BAD_REQUEST);
    }
    return request.refreshToken();
  }
}
