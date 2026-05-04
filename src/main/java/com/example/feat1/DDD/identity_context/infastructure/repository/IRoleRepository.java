package com.example.feat1.DDD.identity_context.infastructure.repository;

import com.example.feat1.DDD.identity_context.infastructure.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IRoleRepository extends JpaRepository<RoleEntity, UUID> {
    Optional<RoleEntity> findByName(String name);
}
