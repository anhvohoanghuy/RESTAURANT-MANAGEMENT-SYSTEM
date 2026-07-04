package com.example.feat1.DDD.auth.infrastructure.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class AuthControllerTest {
  private final AuthService authService = mock(AuthService.class);
  private final RegisterUserUseCase registerUserUseCase = mock(RegisterUserUseCase.class);
  private final AuthAccountRecoveryService authAccountRecoveryService =
      mock(AuthAccountRecoveryService.class);
  private final AuthController controller =
      new AuthController(authService, registerUserUseCase, authAccountRecoveryService);

  @Test
  void registerMapsPublicRequestToLocalUserWithDefaultUserRoleAndReturnsTokenPair() {
    AuthResponse authResponse = new AuthResponse("access", "refresh", "Bearer", 900_000L, 60_000L);
    when(authService.login(any(AuthRequest.class))).thenReturn(authResponse);

    var response =
        controller.register(new RegisterLocalRequest("chinh", "chinh@example.com", "secret"));

    ArgumentCaptor<RegisterRequestDto> captor = ArgumentCaptor.forClass(RegisterRequestDto.class);
    verify(registerUserUseCase).execute(captor.capture());
    verify(authAccountRecoveryService).requestEmailVerification("chinh@example.com");
    RegisterRequestDto mappedRequest = captor.getValue();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(mappedRequest.getUsername()).isEqualTo("chinh");
    assertThat(mappedRequest.getEmail()).isEqualTo("chinh@example.com");
    assertThat(mappedRequest.getPassword()).isEqualTo("secret");
    assertThat(mappedRequest.getProviderUserId()).isEqualTo("chinh");
    assertThat(mappedRequest.getLoginType()).isEqualTo(AuthProvider.LOCAL);
    assertThat(mappedRequest.getRoles()).containsExactly(RoleEnum.USER.getName());
    assertThat(response.getBody()).isEqualTo(authResponse);

    ArgumentCaptor<AuthRequest> loginCaptor = ArgumentCaptor.forClass(AuthRequest.class);
    verify(authService).login(loginCaptor.capture());
    assertThat(loginCaptor.getValue().getAuthType()).isEqualTo(AuthType.LOCAL);
    assertThat(loginCaptor.getValue().getUsername()).isEqualTo("chinh");
    assertThat(loginCaptor.getValue().getPassword()).isEqualTo("secret");
  }

  @Test
  void loginUsesLocalAuthWithoutExposingAuthTypeInPublicRequest() {
    AuthResponse authResponse = new AuthResponse("access", "refresh", "Bearer", 900_000L, 60_000L);
    when(authService.login(any(AuthRequest.class))).thenReturn(authResponse);

    var response = controller.login(new LoginRequest("chinh", "secret"));

    ArgumentCaptor<AuthRequest> captor = ArgumentCaptor.forClass(AuthRequest.class);
    verify(authService).login(captor.capture());
    assertThat(captor.getValue().getAuthType()).isEqualTo(AuthType.LOCAL);
    assertThat(captor.getValue().getUsername()).isEqualTo("chinh");
    assertThat(captor.getValue().getPassword()).isEqualTo("secret");
    assertThat(response.getBody()).isEqualTo(authResponse);
  }

  @Test
  void googleLoginUsesGoogleAuthTypeAndIdToken() {
    AuthResponse authResponse = new AuthResponse("access", "refresh", "Bearer", 900_000L, 60_000L);
    when(authService.login(any(AuthRequest.class))).thenReturn(authResponse);

    var response = controller.google(new GoogleLoginRequest("google-id-token"));

    ArgumentCaptor<AuthRequest> captor = ArgumentCaptor.forClass(AuthRequest.class);
    verify(authService).login(captor.capture());
    assertThat(captor.getValue().getAuthType()).isEqualTo(AuthType.GOOGLE);
    assertThat(captor.getValue().getOathToken()).isEqualTo("google-id-token");
    assertThat(response.getBody()).isEqualTo(authResponse);
  }

  @Test
  void refreshAndLogoutUseJsonRefreshTokenRequest() {
    AuthResponse authResponse = new AuthResponse("access", "refresh2", "Bearer", 900_000L, 60_000L);
    when(authService.refreshToken("refresh1")).thenReturn(authResponse);

    var refreshResponse = controller.refresh(new RefreshTokenRequest("refresh1"));
    var logoutResponse = controller.logout(new RefreshTokenRequest("refresh2"));

    verify(authService).refreshToken("refresh1");
    verify(authService).logout("refresh2");
    assertThat(refreshResponse.getBody()).isEqualTo(authResponse);
    assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void emailVerificationAndPasswordResetEndpointsDelegateToRecoveryService() {
    var verificationRequest =
        controller.requestEmailVerification(new EmailRequest("chinh@example.com"));
    var verifyEmail = controller.verifyEmail(new TokenRequest("verify-token"));
    var forgotPassword = controller.forgotPassword(new EmailRequest("chinh@example.com"));
    var resetPassword =
        controller.resetPassword(new PasswordResetRequest("reset-token", "new-secret"));

    verify(authAccountRecoveryService).requestEmailVerification("chinh@example.com");
    verify(authAccountRecoveryService).verifyEmail("verify-token");
    verify(authAccountRecoveryService).requestPasswordReset("chinh@example.com");
    verify(authAccountRecoveryService).resetPassword("reset-token", "new-secret");
    assertThat(verificationRequest.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(verifyEmail.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(forgotPassword.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(resetPassword.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }
}
