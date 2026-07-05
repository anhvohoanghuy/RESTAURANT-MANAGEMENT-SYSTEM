package com.example.feat1.DDD.auth.infrastructure.entity;

import com.example.feat1.DDD.auth.application.dto.AuthRequestMetadata;
import com.example.feat1.DDD.identity_context.infastructure.entity.UserEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @ToString.Include
  private UUID id;

  @Column(nullable = false, unique = true, length = 512)
  @ToString.Include
  private String token;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(nullable = false)
  @ToString.Include
  private Instant expiryDate;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant revokedAt;

  @Column(length = 512)
  private String replacedByToken;

  @Column(length = 128)
  private String ipAddress;

  @Column(length = 512)
  private String userAgent;

  private Instant lastUsedAt;

  public static RefreshTokenEntity active(String token, UserEntity user, Instant expiryDate) {
    return active(token, user, expiryDate, AuthRequestMetadata.empty());
  }

  public static RefreshTokenEntity active(
      String token, UserEntity user, Instant expiryDate, AuthRequestMetadata metadata) {
    Instant now = Instant.now();
    AuthRequestMetadata safeMetadata = metadata == null ? AuthRequestMetadata.empty() : metadata;
    return new RefreshTokenEntity(
        null,
        token,
        user,
        expiryDate,
        now,
        null,
        null,
        safeMetadata.ipAddress(),
        truncate(safeMetadata.userAgent(), 512),
        now);
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public boolean isExpired(Instant now) {
    return !expiryDate.isAfter(now);
  }

  public boolean isUsable(Instant now) {
    return !isRevoked() && !isExpired(now);
  }

  public void revoke(Instant now) {
    if (revokedAt == null) {
      revokedAt = now;
    }
  }

  public void rotateTo(String newRefreshToken, Instant now) {
    revoke(now);
    replacedByToken = newRefreshToken;
  }

  public void markUsed(Instant now, AuthRequestMetadata metadata) {
    lastUsedAt = now;
    AuthRequestMetadata safeMetadata = metadata == null ? AuthRequestMetadata.empty() : metadata;
    if (safeMetadata.ipAddress() != null) {
      ipAddress = safeMetadata.ipAddress();
    }
    if (safeMetadata.userAgent() != null) {
      userAgent = truncate(safeMetadata.userAgent(), 512);
    }
  }

  private static String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }
}
