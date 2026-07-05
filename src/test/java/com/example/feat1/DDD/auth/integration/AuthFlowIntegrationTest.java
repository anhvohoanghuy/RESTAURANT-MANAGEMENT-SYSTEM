package com.example.feat1.DDD.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.feat1.DDD.auth.application.AuthRateLimitService;
import com.example.feat1.DDD.auth.application.LoginLockoutService;
import com.example.feat1.DDD.auth.application.auth_service.google.GoogleIdTokenVerifier;
import com.example.feat1.DDD.auth.application.auth_service.google.GoogleUserInfo;
import com.example.feat1.DDD.auth.application.auth_service.refresh.RefreshTokenCache;
import com.example.feat1.DDD.auth.application.notification.EmailNotificationPort;
import com.example.feat1.DDD.auth.infrastructure.repository.ISecurityAuditEventRepository;
import com.example.feat1.DDD.identity_context.infastructure.repository.ICredentialRepository;
import com.example.feat1.DDD.identity_context.infastructure.repository.IRefreshTokenRepository;
import com.example.feat1.DDD.identity_context.infastructure.repository.IRoleRepository;
import com.example.feat1.DDD.identity_context.infastructure.repository.IUserRepository;
import com.example.feat1.common.exception.AppException;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "jwt.access-expiration=900000",
      "jwt.refresh-expiration=604800000",
      "jwt.secret=test-secret-test-secret-test-secret-32"
    })
class AuthFlowIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private IUserRepository userRepository;
  @Autowired private ICredentialRepository credentialRepository;
  @Autowired private IRefreshTokenRepository refreshTokenRepository;
  @Autowired private IRoleRepository roleRepository;
  @Autowired private ISecurityAuditEventRepository securityAuditEventRepository;
  @MockitoBean private RefreshTokenCache refreshTokenCache;
  @MockitoBean private GoogleIdTokenVerifier googleIdTokenVerifier;
  @MockitoBean private EmailNotificationPort emailNotificationPort;
  @MockitoBean private AuthRateLimitService authRateLimitService;
  @MockitoBean private LoginLockoutService loginLockoutService;

  @Test
  void startupSeedsUserAndAdminRoles() {
    assertThat(roleRepository.findByName("USER")).isPresent();
    assertThat(roleRepository.findByName("ADMIN")).isPresent();
  }

  @Test
  void registerAutoLogsInAndCreatesUserCredentialRoleAndRefreshToken() throws Exception {
    String username = unique("register");
    String email = username + "@example.com";

    MvcResult result =
        mockMvc
            .perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"username":"%s","email":"%s","password":"secret123"}
                        """
                            .formatted(username, email)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isString())
            .andExpect(jsonPath("$.refreshToken").isString())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.accessExpiresIn").value(900000))
            .andExpect(jsonPath("$.refreshExpiresIn").value(604800000))
            .andReturn();

    String refreshToken =
        JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken");

    var user = userRepository.findByEmail(email).orElseThrow();
    var userWithRoles = userRepository.findByIdWithRoles(user.getId()).orElseThrow();
    assertThat(userWithRoles.getRoles()).extracting("name").containsExactly("USER");
    assertThat(credentialRepository.findByAuthProviderAndProviderUserId("LOCAL", username))
        .isPresent();
    assertThat(refreshTokenRepository.findByToken(refreshToken)).isPresent();
  }

  @Test
  void googleLoginAutoRegistersUserAndBackendTokenWorksForProfile() throws Exception {
    String email = unique("google") + "@gmail.com";
    String googleSubject = "google-sub-" + UUID.randomUUID();
    when(googleIdTokenVerifier.verify("google-id-token"))
        .thenReturn(new GoogleUserInfo(googleSubject, email, true, "Google User", null));

    MvcResult result =
        mockMvc
            .perform(
                post("/auth/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"idToken":"google-id-token"}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isString())
            .andExpect(jsonPath("$.refreshToken").isString())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andReturn();

    String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    String refreshToken =
        JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken");

    var user = userRepository.findByEmail(email).orElseThrow();
    var userWithRoles = userRepository.findByIdWithRoles(user.getId()).orElseThrow();
    assertThat(user.getName()).isEqualTo("Google User");
    assertThat(user.isEmailVerified()).isTrue();
    assertThat(user.getEmailVerifiedAt()).isNotNull();
    assertThat(userWithRoles.getRoles()).extracting("name").containsExactly("USER");
    assertThat(credentialRepository.findByAuthProviderAndProviderUserId("GOOGLE", googleSubject))
        .isPresent();
    assertThat(refreshTokenRepository.findByToken(refreshToken)).isPresent();

    mockMvc
        .perform(get("/users/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.roles[0]").value("USER"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Test
  void registerVerificationTokenVerifiesEmailAndProfileShowsVerifiedState() throws Exception {
    String username = unique("verify");
    String email = username + "@example.com";

    MvcResult result =
        mockMvc
            .perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"username":"%s","email":"%s","password":"secret123"}
                        """
                            .formatted(username, email)))
            .andExpect(status().isCreated())
            .andReturn();

    String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    var userBeforeVerify = userRepository.findByEmail(email).orElseThrow();
    assertThat(userBeforeVerify.isEmailVerified()).isFalse();

    ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
    verify(emailNotificationPort).sendEmailVerification(any(), tokenCaptor.capture());

    mockMvc
        .perform(
            post("/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"token":"%s"}
                    """
                        .formatted(tokenCaptor.getValue())))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/users/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.emailVerified").value(true))
        .andExpect(jsonPath("$.emailVerifiedAt").isString());
  }

  @Test
  void forgotPasswordResetChangesLocalPasswordAndRevokesExistingRefreshTokens() throws Exception {
    String username = unique("reset");
    String email = username + "@example.com";
    String refreshToken = register(username, email, "secret123");

    mockMvc
        .perform(
            post("/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s"}
                    """
                        .formatted(email)))
        .andExpect(status().isAccepted());

    ArgumentCaptor<String> resetTokenCaptor = ArgumentCaptor.forClass(String.class);
    verify(emailNotificationPort).sendPasswordReset(any(), resetTokenCaptor.capture());

    mockMvc
        .perform(
            post("/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"token":"%s","newPassword":"newSecret123"}
                    """
                        .formatted(resetTokenCaptor.getValue())))
        .andExpect(status().isNoContent());

    assertThat(refreshTokenRepository.findByToken(refreshToken).orElseThrow().isRevoked()).isTrue();

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"%s","password":"secret123"}
                    """
                        .formatted(username)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"%s","password":"newSecret123"}
                    """
                        .formatted(username)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isString())
        .andExpect(jsonPath("$.refreshToken").isString());
  }

  @Test
  void loginRefreshLogoutAndProfileFlowWorksThroughHttp() throws Exception {
    String username = unique("flow");
    String email = username + "@example.com";
    register(username, email, "secret123");

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"username":"%s","password":"secret123"}
                        """
                            .formatted(username)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andReturn();

    String accessToken =
        JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
    String refreshToken =
        JsonPath.read(loginResult.getResponse().getContentAsString(), "$.refreshToken");

    mockMvc
        .perform(get("/users/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.roles[0]").value("USER"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());

    MvcResult refreshResult =
        mockMvc
            .perform(
                post("/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"refreshToken":"%s"}
                        """
                            .formatted(refreshToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.refreshToken").isString())
            .andReturn();

    String rotatedRefreshToken =
        JsonPath.read(refreshResult.getResponse().getContentAsString(), "$.refreshToken");
    assertThat(rotatedRefreshToken).isNotEqualTo(refreshToken);
    assertThat(refreshTokenRepository.findByToken(refreshToken).orElseThrow().isRevoked()).isTrue();

    mockMvc
        .perform(
            post("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"refreshToken":"%s"}
                    """
                        .formatted(rotatedRefreshToken)))
        .andExpect(status().isNoContent());

    assertThat(refreshTokenRepository.findByToken(rotatedRefreshToken).orElseThrow().isRevoked())
        .isTrue();
  }

  @Test
  void reusedRotatedRefreshTokenReturnsGlobalErrorContract() throws Exception {
    String username = unique("reuse");
    String email = username + "@example.com";
    String refreshToken = register(username, email, "secret123");

    mockMvc
        .perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"refreshToken":"%s"}
                    """
                        .formatted(refreshToken)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"refreshToken":"%s"}
                    """
                        .formatted(refreshToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"))
        .andExpect(jsonPath("$.message").value("Refresh token reuse detected"))
        .andExpect(jsonPath("$.timestamp").isString());
  }

  @Test
  void repeatedFailedLoginReturnsAccountLocked() throws Exception {
    String username = unique("locked");
    String email = username + "@example.com";
    register(username, email, "secret123");
    doNothing()
        .doNothing()
        .doNothing()
        .doNothing()
        .doThrow(
            new AppException("ACCOUNT_LOCKED", "Account is temporarily locked", HttpStatus.LOCKED))
        .when(loginLockoutService)
        .recordFailure(eq(username));

    for (int index = 0; index < 4; index++) {
      mockMvc
          .perform(
              post("/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"username":"%s","password":"bad-password"}
                      """
                          .formatted(username)))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"%s","password":"bad-password"}
                    """
                        .formatted(username)))
        .andExpect(status().isLocked())
        .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
  }

  @Test
  void excessiveGoogleLoginReturnsRateLimitErrorCode() throws Exception {
    doThrow(
            new AppException(
                "RATE_LIMIT_EXCEEDED", "Rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS))
        .when(authRateLimitService)
        .checkGoogle(any());

    mockMvc
        .perform(
            post("/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"idToken":"google-id-token"}
                    """))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
  }

  @Test
  void loginWritesSecurityAuditEvent() throws Exception {
    String username = unique("audit");
    String email = username + "@example.com";
    register(username, email, "secret123");

    mockMvc
        .perform(
            post("/auth/login")
                .header("User-Agent", "IntegrationTest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"%s","password":"secret123"}
                    """
                        .formatted(username)))
        .andExpect(status().isOk());

    assertThat(securityAuditEventRepository.findAllByPrincipal(username))
        .anySatisfy(
            event -> {
              assertThat(event.getEventType().name()).isEqualTo("LOGIN");
              assertThat(event.getOutcome().name()).isEqualTo("SUCCESS");
              assertThat(event.getUserAgent()).isEqualTo("IntegrationTest");
            });
  }

  @Test
  void userCanListAndRevokeOneOwnedSession() throws Exception {
    String username = unique("session-one");
    String email = username + "@example.com";
    String firstRefreshToken = register(username, email, "secret123");
    MvcResult loginResult = login(username, "secret123");
    String accessToken =
        JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
    var firstSession = refreshTokenRepository.findByToken(firstRefreshToken).orElseThrow();

    MvcResult sessionsResult =
        mockMvc
            .perform(get("/auth/sessions").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].sessionId").isString())
            .andReturn();

    assertThat(sessionsResult.getResponse().getContentAsString()).doesNotContain(firstRefreshToken);

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/auth/sessions/{sessionId}", firstSession.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNoContent());

    assertThat(refreshTokenRepository.findByToken(firstRefreshToken).orElseThrow().isRevoked())
        .isTrue();
  }

  @Test
  void userCanRevokeOtherSessionsExceptCurrentRefreshToken() throws Exception {
    String username = unique("session-others");
    String email = username + "@example.com";
    String firstRefreshToken = register(username, email, "secret123");
    MvcResult loginResult = login(username, "secret123");
    String accessToken =
        JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
    String currentRefreshToken =
        JsonPath.read(loginResult.getResponse().getContentAsString(), "$.refreshToken");

    mockMvc
        .perform(
            post("/auth/sessions/revoke-others")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"refreshToken":"%s"}
                    """
                        .formatted(currentRefreshToken)))
        .andExpect(status().isNoContent());

    assertThat(refreshTokenRepository.findByToken(firstRefreshToken).orElseThrow().isRevoked())
        .isTrue();
    assertThat(refreshTokenRepository.findByToken(currentRefreshToken).orElseThrow().isRevoked())
        .isFalse();
  }

  @Test
  void unauthenticatedProfileReturnsGlobalSecurityErrorContract() throws Exception {
    mockMvc
        .perform(get("/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.message").value("Authentication is required"))
        .andExpect(jsonPath("$.timestamp").isString());
  }

  @Test
  void userRoleCannotAccessAdminRoutesAndReceivesGlobalForbiddenError() throws Exception {
    String username = unique("forbidden");
    String email = username + "@example.com";
    register(username, email, "secret123");
    MvcResult loginResult = login(username, "secret123");
    String accessToken =
        JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");

    mockMvc
        .perform(get("/admin/phase-02-check").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.message").value("Access is denied"))
        .andExpect(jsonPath("$.timestamp").isString());
  }

  private String register(String username, String email, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"username":"%s","email":"%s","password":"%s"}
                        """
                            .formatted(username, email, password)))
            .andExpect(status().isCreated())
            .andReturn();

    return JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken");
  }

  private MvcResult login(String username, String password) throws Exception {
    return mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"%s","password":"%s"}
                    """
                        .formatted(username, password)))
        .andExpect(status().isOk())
        .andReturn();
  }

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID();
  }
}
