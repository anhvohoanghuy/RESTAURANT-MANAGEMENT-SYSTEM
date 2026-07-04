package com.example.feat1.DDD.identity_context.infastructure.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "users")
public class UserEntity {

  @Id @ToString.Include private UUID id;

  @Column(nullable = false)
  @ToString.Include
  private String name;

  @Column(nullable = false, unique = true)
  @ToString.Include
  private String email;

  @Column(nullable = false, columnDefinition = "boolean default false")
  private boolean emailVerified;

  private Instant emailVerifiedAt;

  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<UserRoleEntity> userRoles = new java.util.HashSet<>();

  public Set<RoleEntity> getRoles() {
    return userRoles.stream().map(UserRoleEntity::getRole).collect(Collectors.toSet());
  }
}
