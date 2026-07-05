package com.example.feat1.DDD.auth.application.auth_service.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.feat1.DDD.identity_context.domain.model.aggregate.User;
import com.example.feat1.DDD.identity_context.domain.model.entity.Permission;
import com.example.feat1.DDD.identity_context.domain.model.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtProviderTest {
  @Test
  void accessTokenContainsIdentityRolesPermissionsAndTokenMetadataClaims() {
    JwtProvider jwtProvider = new JwtProvider(properties());
    UUID userId = UUID.randomUUID();
    Role userRole =
        new Role(UUID.randomUUID(), "USER", Set.of(new Permission(UUID.randomUUID(), "menu:read")));
    User user = User.register("chinh", "chinh@example.com");
    user.setId(userId);
    user.assignRole(userRole);

    String token = jwtProvider.generateToken(user, TokenType.ACCESS);
    Claims claims = parseClaims(token);

    assertThat(claims.getSubject()).isEqualTo(userId.toString());
    assertThat(claims.getIssuedAt()).isNotNull();
    assertThat(claims.getExpiration()).isNotNull();
    assertThat(claims.getId()).isNotBlank();
    assertThat(claims.get("tokenType", String.class)).isEqualTo(TokenType.ACCESS.name());
    assertThat(claims.get("roles", List.class)).containsExactly("USER");
    assertThat(claims.get("permissions", List.class)).containsExactly("menu:read");
  }

  @Test
  void refreshTokenOmitsPermissionClaimsAndKeepsRefreshType() {
    JwtProvider jwtProvider = new JwtProvider(properties());
    User user = User.register("chinh", "chinh@example.com");
    user.setId(UUID.randomUUID());
    user.assignRole(
        new Role(
            UUID.randomUUID(), "USER", Set.of(new Permission(UUID.randomUUID(), "menu:read"))));

    String token = jwtProvider.generateToken(user, TokenType.REFRESH);
    Claims claims = parseClaims(token);

    assertThat(claims.get("tokenType", String.class)).isEqualTo(TokenType.REFRESH.name());
    assertThat(claims.get("roles", List.class)).containsExactly("USER");
    assertThat(claims.get("permissions")).isNull();
  }

  private JwtProperties properties() {
    JwtProperties properties = new JwtProperties();
    properties.setAccessExpiration(900_000L);
    properties.setRefreshExpiration(604_800_000L);
    properties.setSecret("test-secret-test-secret-test-secret-32");
    return properties;
  }

  private Claims parseClaims(String token) {
    SecretKey key = Keys.hmacShaKeyFor(properties().getSecret().getBytes());
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }
}
