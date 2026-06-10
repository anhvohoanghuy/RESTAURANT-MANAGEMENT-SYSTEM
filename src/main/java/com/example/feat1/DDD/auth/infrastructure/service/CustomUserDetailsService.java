package com.example.feat1.DDD.auth.infrastructure.service;

import com.example.feat1.DDD.auth.infrastructure.security.CustomUserDetails;
import com.example.feat1.DDD.identity_context.domain.model.aggregate.User;
import com.example.feat1.DDD.identity_context.domain.model.entity.Credential;
import com.example.feat1.DDD.identity_context.domain.model.entity.Role;
import com.example.feat1.DDD.identity_context.domain.repository.credential.ICredentialDomainRepository;
import com.example.feat1.DDD.identity_context.domain.repository.user.IUserDomainRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService {
  private final IUserDomainRepository userDomainRepository;
  private final ICredentialDomainRepository credentialDomainRepository;

  public CustomUserDetails loadUserById(UUID id) {
    Optional<User> userDomain = userDomainRepository.findById(id);
    Optional<Credential> credentialDomain = credentialDomainRepository.findByUserId(id);
    if (userDomain.isEmpty() || credentialDomain.isEmpty()) {
      throw new RuntimeException("User not found");
    }
    return CustomUserDetails.builder()
        .id(userDomain.get().getId())
        .email(userDomain.get().getEmail())
        .roles(userDomain.get().getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
        .password(credentialDomain.get().getPasswordHash())
        .build();
  }
}
