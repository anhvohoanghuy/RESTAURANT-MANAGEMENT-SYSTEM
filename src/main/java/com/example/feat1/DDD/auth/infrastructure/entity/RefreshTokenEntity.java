package com.example.feat1.DDD.auth.infrastructure.entity;

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

  public static RefreshTokenEntity active(String token, UserEntity user, Instant expiryDate) {
    return new RefreshTokenEntity(null, token, user, expiryDate, Instant.now(), null, null);
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
}
