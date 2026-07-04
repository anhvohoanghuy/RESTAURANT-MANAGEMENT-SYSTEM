package com.example.feat1.DDD.identity_context.infastructure.repository;

import com.example.feat1.DDD.identity_context.infastructure.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IUserRepository extends JpaRepository<UserEntity, UUID> {
  @Query(
      """
            SELECT u FROM UserEntity u
            WHERE u.email = :email
            """)
  Optional<UserEntity> findByEmail(String email);

  Optional<UserEntity> findById(@NonNull UUID id);

  @Query(
      """
                SELECT DISTINCT u
                FROM UserEntity u
                JOIN FETCH u.userRoles ur
                JOIN FETCH ur.role r
                LEFT JOIN FETCH r.rolePermissions rp
                LEFT JOIN FETCH rp.permission
                WHERE u.id = :id
            """)
  Optional<UserEntity> findByIdWithRoles(UUID id);

  @Query(
      """
                SELECT DISTINCT u
                FROM UserEntity u
                JOIN FETCH u.userRoles ur
                JOIN FETCH ur.role r
                LEFT JOIN FETCH r.rolePermissions rp
                LEFT JOIN FETCH rp.permission
                WHERE u.email = :email
            """)
  Optional<UserEntity> findByEmailWithRoles(String email);
}
