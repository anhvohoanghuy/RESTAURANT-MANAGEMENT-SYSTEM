package com.example.feat1.DDD.identity_context.infastructure.mapper;

import com.example.feat1.DDD.identity_context.domain.model.aggregate.User;
import com.example.feat1.DDD.identity_context.domain.repository.user.IUserDomainRepository;
import com.example.feat1.DDD.identity_context.infastructure.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final IUserDomainRepository userRepository;

    public Optional<User> getUserById(UUID id) {
        return userRepository.findByIdWithRoles(id);
    }

    public void createUser() {

    }
}
