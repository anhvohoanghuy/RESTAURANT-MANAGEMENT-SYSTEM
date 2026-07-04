package com.example.feat1.DDD.auth.infrastructure.repository;

import com.example.feat1.DDD.auth.domain.model.AuthActionTokenPurpose;
import com.example.feat1.DDD.auth.infrastructure.entity.AuthActionTokenEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IAuthActionTokenRepository extends JpaRepository<AuthActionTokenEntity, UUID> {
  Optional<AuthActionTokenEntity> findByTokenHashAndPurpose(
      String tokenHash, AuthActionTokenPurpose purpose);
}
