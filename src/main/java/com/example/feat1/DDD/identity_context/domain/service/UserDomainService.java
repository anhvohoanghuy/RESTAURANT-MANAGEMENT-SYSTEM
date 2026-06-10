package com.example.feat1.DDD.identity_context.domain.service;

import com.example.feat1.DDD.identity_context.domain.model.aggregate.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDomainService {

  public void validateUser(User user) {
    if (user == null) {
      throw new IllegalArgumentException("User cannot be null");
    }
    if (user.getName() == null || user.getName().isBlank()) {
      throw new IllegalArgumentException("User name cannot be empty");
    }
    String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    if (user.getEmail() == null
        || user.getEmail().isBlank()
        || !user.getEmail().matches(EMAIL_REGEX)) {
      throw new IllegalArgumentException("Invalid email format");
    }
  }
}
