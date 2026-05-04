package com.example.feat1.DDD.identity_context.infastructure.repository;

import com.example.feat1.DDD.identity_context.infastructure.entity.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IPermissionRepository extends JpaRepository<PermissionEntity, UUID> {
    Optional<PermissionEntity> findByName(String name);
}
