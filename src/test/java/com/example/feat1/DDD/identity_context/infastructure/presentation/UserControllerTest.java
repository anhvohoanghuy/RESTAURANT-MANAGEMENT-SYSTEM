package com.example.feat1.DDD.identity_context.infastructure.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.auth.infrastructure.security.CustomUserDetails;
import com.example.feat1.DDD.identity_context.domain.model.aggregate.User;
import com.example.feat1.DDD.identity_context.domain.model.entity.Role;
import com.example.feat1.DDD.identity_context.infastructure.mapper.UserService;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserControllerTest {
  private final UserService userService = mock(UserService.class);
  private final UserController controller = new UserController(userService);

  @Test
  void currentUserReturnsSafeProfileDto() {
    UUID userId = UUID.randomUUID();
    User user = new User(userId, "Chinh", "chinh@example.com", new HashSet<>());
    user.assignRole(new Role(UUID.randomUUID(), "USER"));
    CustomUserDetails principal =
        CustomUserDetails.builder()
            .id(userId)
            .email("chinh@example.com")
            .password("hash-should-not-leak")
            .roles(Set.of("USER"))
            .build();

    when(userService.getUserById(userId)).thenReturn(Optional.of(user));

    var profile = controller.getCurrentUser(principal);

    assertThat(profile.id()).isEqualTo(userId);
    assertThat(profile.name()).isEqualTo("Chinh");
    assertThat(profile.email()).isEqualTo("chinh@example.com");
    assertThat(profile.roles()).containsExactly("USER");
    assertThat(profile.toString()).doesNotContain("hash-should-not-leak");
  }
}
