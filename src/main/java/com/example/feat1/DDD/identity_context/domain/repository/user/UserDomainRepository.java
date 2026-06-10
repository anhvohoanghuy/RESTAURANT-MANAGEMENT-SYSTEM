package com.example.feat1.DDD.identity_context.domain.repository.user;

import com.example.feat1.DDD.identity_context.domain.model.aggregate.User;
import com.example.feat1.DDD.identity_context.infastructure.entity.UserEntity;
import com.example.feat1.DDD.identity_context.infastructure.mapper.UserMapper;
import com.example.feat1.DDD.identity_context.infastructure.repository.IUserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserDomainRepository implements IUserDomainRepository {
  private final IUserRepository jpaRepo;

  @Override
  public Optional<User> findByEmail(String email) {
    return UserMapper.userToDomain(jpaRepo.findByEmail(email).orElse(null));
  }

  @Override
  public Optional<User> findByIdWithRoles(UUID id) {
    return UserMapper.userToDomain(jpaRepo.findByIdWithRoles(id).orElse(null));
  }

  @Override
  public Optional<User> findById(UUID id) {
    return UserMapper.userToDomain(jpaRepo.findById(id).orElse(null));
  }

  @Override
  public void save(User user) {
    UserEntity entity = UserMapper.userToEntity(user);
    jpaRepo.save(entity);
  }

  @Override
  public void delete(User user) {
    UserEntity entity = UserMapper.userToEntity(user);
    jpaRepo.delete(entity);
  }
}
