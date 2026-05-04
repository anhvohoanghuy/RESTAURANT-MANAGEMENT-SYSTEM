package com.example.feat1.DDD.identity_context.domain.repository.credential;

import com.example.feat1.DDD.identity_context.domain.model.enums.AuthProvider;
import com.example.feat1.DDD.identity_context.domain.model.entity.Credential;
import com.example.feat1.DDD.identity_context.infastructure.entity.CredentialEntity;
import com.example.feat1.DDD.identity_context.infastructure.mapper.CredentialMapper;
import com.example.feat1.DDD.identity_context.infastructure.mapper.UserMapper;
import com.example.feat1.DDD.identity_context.infastructure.repository.ICredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CredentialRepository implements ICredentialDomainRepository{
    private final ICredentialRepository jpaRepo;

    @Override
    public void save(Credential credential) {
        CredentialEntity entity = CredentialMapper.toEntity(credential);
        if (entity != null) {
            jpaRepo.save(entity);
        } else {
            throw new IllegalArgumentException("Credential cannot be null");
        }
    }

    @Override
    public Optional<Credential> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId) {
        Optional<CredentialEntity> entity =
                jpaRepo.findByAuthProviderAndProviderUserId(provider.name(), providerUserId);

        return entity.map(CredentialMapper::toDomain);
    }

    @Override
    public Optional<Credential> findByUserId(UUID userId) {
        return jpaRepo.findByUserId(userId).map(CredentialMapper::toDomain);
    }
}
