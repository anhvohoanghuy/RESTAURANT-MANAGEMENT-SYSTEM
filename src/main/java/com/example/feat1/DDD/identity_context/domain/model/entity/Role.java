package com.example.feat1.DDD.identity_context.domain.model.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Role {
  private UUID id;
  private String name;
  private Set<Permission> permissions = new HashSet<>();

  public Role(UUID id, String name) {
    this.id = id;
    this.name = name;
  }

  public Role(UUID id, String name, Set<Permission> permissions) {
    this.id = id;
    this.name = name;
    this.permissions = permissions == null ? new HashSet<>() : permissions;
  }

  public static Role create(String name) {
    return new Role(UUID.randomUUID(), name);
  }

  public Set<String> getPermissionsCode() {
    return permissions.stream().map(Permission::getCode).collect(Collectors.toSet());
  }
}
