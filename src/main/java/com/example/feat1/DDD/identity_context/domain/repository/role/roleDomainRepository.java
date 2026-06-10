package com.example.feat1.DDD.identity_context.domain.repository.role;

import com.example.feat1.DDD.identity_context.domain.model.entity.Role;
import com.example.feat1.DDD.identity_context.infastructure.entity.RoleEntity;
import com.example.feat1.DDD.identity_context.infastructure.mapper.UserMapper;
import com.example.feat1.DDD.identity_context.infastructure.repository.IRoleRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class roleDomainRepository implements IRoleDomainRepository {
  private final IRoleRepository jpaRepo;

  @Override
  public void save(String name) {
    Role role = Role.create(name);
    jpaRepo.save(UserMapper.roleToEntity(role));
  }

  @Override
  public void delete(String name) {}

  @Override
  public Optional<Role> findByName(String name) {
    return jpaRepo.findByName(name).map(UserMapper::roleToDomain);
  }

  @Override
  public Role findReferenceById(UUID id) {
    RoleEntity entity = jpaRepo.getReferenceById(id);
    return UserMapper.roleToDomain(entity);
  }
}
