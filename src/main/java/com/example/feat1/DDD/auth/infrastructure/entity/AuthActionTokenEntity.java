package com.example.feat1.DDD.auth.infrastructure.entity;

import com.example.feat1.DDD.auth.domain.model.AuthActionTokenPurpose;
import com.example.feat1.DDD.identity_context.infastructure.entity.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "auth_action_tokens")
public class AuthActionTokenEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @ToString.Include
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(nullable = false, unique = true, length = 128)
  private String tokenHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 64)
  private AuthActionTokenPurpose purpose;

  @Column(nullable = false)
  private Instant expiresAt;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant consumedAt;

  public static AuthActionTokenEntity create(
      UserEntity user,
      String tokenHash,
      AuthActionTokenPurpose purpose,
      Instant expiresAt,
      Instant createdAt) {
    return new AuthActionTokenEntity(null, user, tokenHash, purpose, expiresAt, createdAt, null);
  }

  public boolean isExpired(Instant now) {
    return !expiresAt.isAfter(now);
  }

  public boolean isConsumed() {
    return consumedAt != null;
  }

  public void consume(Instant now) {
    if (consumedAt == null) {
      consumedAt = now;
    }
  }
}
