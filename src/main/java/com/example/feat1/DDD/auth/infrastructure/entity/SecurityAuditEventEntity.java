package com.example.feat1.DDD.auth.infrastructure.entity;

import com.example.feat1.DDD.auth.application.dto.AuthRequestMetadata;
import com.example.feat1.DDD.auth.domain.model.SecurityEventOutcome;
import com.example.feat1.DDD.auth.domain.model.SecurityEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "security_audit_events")
public class SecurityAuditEventEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 64)
  private SecurityEventType eventType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private SecurityEventOutcome outcome;

  private UUID userId;

  @Column(length = 320)
  private String principal;

  @Column(length = 128)
  private String ipAddress;

  @Column(length = 512)
  private String userAgent;

  @Column(length = 512)
  private String reason;

  @Column(nullable = false)
  private Instant createdAt;

  public static SecurityAuditEventEntity create(
      SecurityEventType eventType,
      SecurityEventOutcome outcome,
      UUID userId,
      String principal,
      AuthRequestMetadata metadata,
      String reason,
      Instant now) {
    AuthRequestMetadata safeMetadata = metadata == null ? AuthRequestMetadata.empty() : metadata;
    return new SecurityAuditEventEntity(
        null,
        eventType,
        outcome,
        userId,
        principal,
        safeMetadata.ipAddress(),
        truncate(safeMetadata.userAgent(), 512),
        reason,
        now);
  }

  private static String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }
}
