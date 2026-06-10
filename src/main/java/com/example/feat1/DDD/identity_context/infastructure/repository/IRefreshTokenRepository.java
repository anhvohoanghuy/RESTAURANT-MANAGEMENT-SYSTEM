package com.example.feat1.DDD.identity_context.infastructure.repository;

import com.example.feat1.DDD.auth.infrastructure.entity.RefreshTokenEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IRefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
  Optional<RefreshTokenEntity> findByToken(String token);

  Optional<RefreshTokenEntity> findByUser_Id(UUID userId);

  void deleteByUser_Id(UUID userId);
}
