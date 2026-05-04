package com.example.feat1.DDD.identity_context.infastructure.repository;

import com.example.feat1.DDD.identity_context.infastructure.entity.CredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ICredentialRepository extends JpaRepository<CredentialEntity, UUID> {
    Optional<CredentialEntity> findByAuthProviderAndProviderUserId(String provider, String providerUserId);
    Optional<CredentialEntity> findByUserId(UUID userId);
}
