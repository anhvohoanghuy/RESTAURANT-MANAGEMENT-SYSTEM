package com.example.feat1.DDD.auth.application.auth_service.jwt;

import com.example.feat1.DDD.identity_context.domain.model.aggregate.User;
import com.example.feat1.DDD.identity_context.domain.model.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class JwtProvider {
  private final JwtProperties jwtProperties;

  public long getExpiration(TokenType type) {
    return switch (type) {
      case ACCESS -> jwtProperties.getAccessExpiration();
      case REFRESH -> jwtProperties.getRefreshExpiration();
    };
  }

  public String generateToken(User user, TokenType type) {
    List<String> permissions =
        user.getRoles().stream()
            .map(Role::getPermissionsCode)
            .flatMap(java.util.Collection::stream)
            .toList();

    List<String> roles = user.getRoles().stream().map(Role::getName).toList();

    long expiration = getExpiration(type);
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + expiration);

    JwtBuilder builder =
        Jwts.builder()
            .subject(user.getId().toString())
            .claim("role", roles)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(getSigningKey())
            .id(UUID.randomUUID().toString());
    if (type == TokenType.ACCESS) {
      builder.claim("permissions", permissions);
    }
    return builder.compact();
  }

  private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());
  }

  public boolean validateToken(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (JwtException e) {
      return false;
    }
  }

  public String extractRole(String token) {
    return parseClaims(token).get("role", String.class);
  }

  public String extractPermission(String token) {
    return parseClaims(token).get("permissions", String.class);
  }

  public UUID extractUserId(String token) {
    Claims claims = parseClaims(token);
    return UUID.fromString(claims.getSubject());
  }

  private Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
  }
}
