package com.example.feat1.DDD.auth.infrastructure.repository;

import com.example.feat1.DDD.auth.infrastructure.entity.SecurityAuditEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ISecurityAuditEventRepository
    extends JpaRepository<SecurityAuditEventEntity, UUID> {
  List<SecurityAuditEventEntity> findAllByPrincipal(String principal);
}
