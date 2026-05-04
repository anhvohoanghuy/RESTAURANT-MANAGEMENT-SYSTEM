package com.example.feat1.DDD.identity_context.domain.repository.credential;

import com.example.feat1.DDD.identity_context.domain.model.enums.AuthProvider;
import com.example.feat1.DDD.identity_context.domain.model.entity.Credential;

import java.util.Optional;

public interface ICredentialDomainRepository {
    void save(Credential credential);
    Optional<Credential> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
    Optional<Credential> findByUserId(java.util.UUID userId);
}
