package com.example.feat1.DDD.identity_context.infastructure.presentation;

import com.example.feat1.DDD.auth.infrastructure.security.CustomUserDetails;
import com.example.feat1.DDD.identity_context.application.dto.UserProfileResponse;
import com.example.feat1.DDD.identity_context.domain.model.aggregate.User;
import com.example.feat1.DDD.identity_context.domain.model.entity.Role;
import com.example.feat1.DDD.identity_context.infastructure.mapper.UserService;
import com.example.feat1.common.exception.AppException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @GetMapping("/me")
  public UserProfileResponse getCurrentUser(@AuthenticationPrincipal CustomUserDetails principal) {
    if (principal == null) {
      throw new AppException(
          "UNAUTHENTICATED", "Authentication is required", HttpStatus.UNAUTHORIZED);
    }

    User user =
        userService
            .getUserById(principal.getId())
            .orElseThrow(
                () -> new AppException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));

    return new UserProfileResponse(
        user.getId(),
        user.getName(),
        user.getEmail(),
        user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()));
  }

  @GetMapping("/{id}")
  public Optional<User> getUser(@PathVariable UUID id) {
    return userService.getUserById(id);
  }
}
