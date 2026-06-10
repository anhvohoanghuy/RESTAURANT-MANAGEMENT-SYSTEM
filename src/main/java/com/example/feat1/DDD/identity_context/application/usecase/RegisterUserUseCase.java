package com.example.feat1.DDD.identity_context.application.usecase;

import com.example.feat1.DDD.identity_context.application.dto.RegisterRequestDto;
import com.example.feat1.DDD.identity_context.application.dto.RoleEnum;
import com.example.feat1.DDD.identity_context.domain.model.aggregate.User;
import com.example.feat1.DDD.identity_context.domain.model.entity.Credential;
import com.example.feat1.DDD.identity_context.domain.model.entity.Role;
import com.example.feat1.DDD.identity_context.domain.repository.credential.ICredentialDomainRepository;
import com.example.feat1.DDD.identity_context.domain.repository.role.IRoleDomainRepository;
import com.example.feat1.DDD.identity_context.domain.repository.user.IUserDomainRepository;
import com.example.feat1.DDD.identity_context.domain.service.UserDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class RegisterUserUseCase {
  private final UserDomainService userDomainService;
  private final IUserDomainRepository userDomainRepository;
  private final IRoleDomainRepository roleDomainRepository;
  private final PasswordEncoder passwordEncoder;
  private final ICredentialDomainRepository credentialDomainRepository;

  public void execute(RegisterRequestDto requestDto) {
    User user = User.register(requestDto.getUsername(), requestDto.getEmail());
    userDomainService.validateUser(user);

    requestDto
        .getRoles()
        .forEach(
            roleName -> {
              RoleEnum roleEnum = RoleEnum.fromName(roleName);
              Role role = roleDomainRepository.findReferenceById(roleEnum.getId());
              user.assignRole(role);
            });
    userDomainRepository.save(user);

    Credential credential =
        Credential.create(
            user.getId(),
            requestDto.getLoginType(),
            requestDto.getProviderUserId(),
            passwordEncoder.encode(requestDto.getPassword()));
    credentialDomainRepository.save(credential);
  }
}
