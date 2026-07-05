package com.example.feat1.DDD.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.auth.application.dto.AuthRequestMetadata;
import com.example.feat1.DDD.auth.domain.model.SecurityEventOutcome;
import com.example.feat1.DDD.auth.domain.model.SecurityEventType;
import com.example.feat1.DDD.auth.infrastructure.entity.SecurityAuditEventEntity;
import com.example.feat1.DDD.auth.infrastructure.repository.ISecurityAuditEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class SecurityAuditServiceTest {
  private final ISecurityAuditEventRepository repository =
      Mockito.mock(ISecurityAuditEventRepository.class);
  private final SecurityAuditService service = new SecurityAuditService(repository);

  @Test
  void persistsSecurityEventWithMetadata() {
    service.record(
        SecurityEventType.LOGIN,
        SecurityEventOutcome.SUCCESS,
        null,
        "chinh",
        new AuthRequestMetadata("10.0.0.1", "JUnit"),
        "ok");

    ArgumentCaptor<SecurityAuditEventEntity> captor =
        ArgumentCaptor.forClass(SecurityAuditEventEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getEventType()).isEqualTo(SecurityEventType.LOGIN);
    assertThat(captor.getValue().getOutcome()).isEqualTo(SecurityEventOutcome.SUCCESS);
    assertThat(captor.getValue().getPrincipal()).isEqualTo("chinh");
    assertThat(captor.getValue().getIpAddress()).isEqualTo("10.0.0.1");
    assertThat(captor.getValue().getUserAgent()).isEqualTo("JUnit");
    assertThat(captor.getValue().getReason()).isEqualTo("ok");
  }

  @Test
  void auditWriteFailureDoesNotEscape() {
    when(repository.save(any())).thenThrow(new IllegalStateException("db down"));

    service.record(
        SecurityEventType.LOGIN,
        SecurityEventOutcome.FAILURE,
        null,
        "chinh",
        AuthRequestMetadata.empty(),
        "INVALID_CREDENTIALS");
  }
}
