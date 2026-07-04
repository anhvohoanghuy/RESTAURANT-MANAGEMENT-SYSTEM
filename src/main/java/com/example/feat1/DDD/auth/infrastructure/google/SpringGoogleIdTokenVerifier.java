package com.example.feat1.DDD.auth.infrastructure.google;

import com.example.feat1.DDD.auth.application.auth_service.google.GoogleIdTokenVerifier;
import com.example.feat1.DDD.auth.application.auth_service.google.GoogleUserInfo;
import com.example.feat1.common.exception.AppException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class SpringGoogleIdTokenVerifier implements GoogleIdTokenVerifier {
  private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
  private static final List<String> GOOGLE_ISSUERS =
      List.of("https://accounts.google.com", "accounts.google.com");

  private final JwtDecoder jwtDecoder;
  private final List<String> acceptedAudiences;

  public SpringGoogleIdTokenVerifier(
      @Value("${google.oauth.client-ids:}") String acceptedAudiences) {
    this.jwtDecoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI).build();
    this.acceptedAudiences =
        Arrays.stream(acceptedAudiences.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
  }

  @Override
  public GoogleUserInfo verify(String idToken) {
    Jwt jwt;
    try {
      jwt = jwtDecoder.decode(idToken);
    } catch (JwtException exception) {
      throw invalidToken();
    }

    if (jwt.getIssuer() == null || !GOOGLE_ISSUERS.contains(jwt.getIssuer().toString())) {
      throw invalidToken();
    }

    if (acceptedAudiences.isEmpty()
        || jwt.getAudience().stream().noneMatch(acceptedAudiences::contains)) {
      throw invalidToken();
    }

    String subject = jwt.getSubject();
    String email = jwt.getClaimAsString("email");
    Boolean emailVerified = jwt.getClaim("email_verified");
    String name = jwt.getClaimAsString("name");
    String hostedDomain = jwt.getClaimAsString("hd");

    if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
      throw invalidToken();
    }

    if (!Boolean.TRUE.equals(emailVerified)) {
      throw new AppException(
          "GOOGLE_EMAIL_UNVERIFIED", "Google email is not verified", HttpStatus.UNAUTHORIZED);
    }

    return new GoogleUserInfo(subject, email, true, name, hostedDomain);
  }

  private AppException invalidToken() {
    return new AppException(
        "GOOGLE_TOKEN_INVALID", "Google token is invalid", HttpStatus.UNAUTHORIZED);
  }
}
