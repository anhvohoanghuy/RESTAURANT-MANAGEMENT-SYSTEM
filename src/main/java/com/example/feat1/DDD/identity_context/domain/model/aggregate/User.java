package com.example.feat1.DDD.identity_context.domain.model.aggregate;

import com.example.feat1.DDD.identity_context.domain.model.entity.Role;
import com.example.feat1.DDD.identity_context.domain.model.entity.UserRole;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
  private UUID id;
  private String name;
  private String email;
  private boolean emailVerified;
  private Instant emailVerifiedAt;
  private Set<UserRole> userRoles = new HashSet<>();

  public User(UUID id, String name, String email, Set<UserRole> userRoles) {
    this(id, name, email, false, null, userRoles);
  }

  private User(String name, String email) {
    this.id = UUID.randomUUID();
    this.name = name;
    this.email = email;
    this.emailVerified = false;
  }

  public static User register(String name, String email) {
    return new User(name, email);
  }

  public void assignRole(Role role) {
    UserRole userRole = new UserRole(this, role);
    this.userRoles.add(userRole);
  }

  public void markEmailVerified(Instant verifiedAt) {
    this.emailVerified = true;
    this.emailVerifiedAt = verifiedAt;
  }

  public Set<Role> getRoles() {
    return userRoles.stream()
        .map(UserRole::getRole)
        .collect(HashSet::new, HashSet::add, HashSet::addAll);
  }
}
