package com.example.feat1.DDD.identity_context.infastructure.repository;

import com.example.feat1.DDD.identity_context.infastructure.entity.UserEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

public interface IUserRepository extends JpaRepository<UserEntity, UUID> {
    @Query("""
            SELECT u FROM UserEntity u
            WHERE u.email = :email
            """)
    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findById(@NonNull UUID id);

    @Query("""
                SELECT DISTINCT u
                FROM UserEntity u
                JOIN FETCH u.userRoles ur
                JOIN FETCH ur.role r
                WHERE u.id = :id
            """)
    Optional<UserEntity> findByIdWithRoles(UUID id);
}
