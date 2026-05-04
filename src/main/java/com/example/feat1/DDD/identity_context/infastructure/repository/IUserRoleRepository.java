package com.example.feat1.DDD.identity_context.infastructure.repository;

import com.example.feat1.DDD.identity_context.infastructure.entity.UserRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IUserRoleRepository extends JpaRepository<UserRoleEntity, UUID> {
    List<UserRoleEntity> findByUser_Id(UUID userId);
}
