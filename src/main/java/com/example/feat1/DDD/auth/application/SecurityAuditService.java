package com.example.feat1.DDD.auth.application;

import com.example.feat1.DDD.auth.application.dto.AuthRequestMetadata;
import com.example.feat1.DDD.auth.domain.model.SecurityEventOutcome;
import com.example.feat1.DDD.auth.domain.model.SecurityEventType;
import com.example.feat1.DDD.auth.infrastructure.entity.SecurityAuditEventEntity;
import com.example.feat1.DDD.auth.infrastructure.repository.ISecurityAuditEventRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityAuditService {
  private final ISecurityAuditEventRepository securityAuditEventRepository;

  public void record(
      SecurityEventType eventType,
      SecurityEventOutcome outcome,
      UUID userId,
      String principal,
      AuthRequestMetadata metadata,
      String reason) {
    try {
      securityAuditEventRepository.save(
          SecurityAuditEventEntity.create(
              eventType, outcome, userId, principal, metadata, reason, Instant.now()));
    } catch (RuntimeException exception) {
      log.warn(
          "Security audit write failed for eventType={}, principal={}",
          eventType,
          principal,
          exception);
    }
  }
}
