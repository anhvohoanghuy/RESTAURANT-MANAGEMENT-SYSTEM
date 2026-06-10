package com.example.feat1.DDD.identity_context.domain.repository.user;

import com.example.feat1.DDD.identity_context.domain.model.aggregate.User;
import java.util.Optional;
import java.util.UUID;

public interface IUserDomainRepository {
  Optional<User> findByEmail(String email);

  Optional<User> findByIdWithRoles(java.util.UUID id);

  Optional<User> findById(UUID id);

  void save(User user);

  void delete(User user);
}
