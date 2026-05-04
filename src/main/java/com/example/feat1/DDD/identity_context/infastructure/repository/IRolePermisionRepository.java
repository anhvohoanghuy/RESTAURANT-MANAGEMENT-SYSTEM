package com.example.feat1.DDD.identity_context.infastructure.repository;

import com.example.feat1.DDD.identity_context.infastructure.entity.RolePermisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IRolePermisionRepository extends JpaRepository<RolePermisionEntity, UUID> {
    void deleteByRole_Id(UUID roleId);
    void deleteByPermission_Id(UUID permissionId);
    void deleteByRole_IdAndPermission_Id(UUID roleId, UUID permissionId);
}
