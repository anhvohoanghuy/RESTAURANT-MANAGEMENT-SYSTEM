package com.example.feat1.DDD.identity_context.infastructure.entity;

import jakarta.persistence.*;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class UserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String email;

  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<UserRoleEntity> userRoles = new java.util.HashSet<>();

  public Set<RoleEntity> getRoles() {
    return userRoles.stream().map(UserRoleEntity::getRole).collect(Collectors.toSet());
  }
}
