package com.example.feat1.DDD.identity_context.infastructure.mapper;

import com.example.feat1.DDD.identity_context.domain.model.aggregate.User;
import com.example.feat1.DDD.identity_context.domain.model.entity.Permission;
import com.example.feat1.DDD.identity_context.domain.model.entity.Role;
import com.example.feat1.DDD.identity_context.domain.model.entity.UserRole;
import com.example.feat1.DDD.identity_context.infastructure.entity.PermissionEntity;
import com.example.feat1.DDD.identity_context.infastructure.entity.RoleEntity;
import com.example.feat1.DDD.identity_context.infastructure.entity.UserEntity;
import com.example.feat1.DDD.identity_context.infastructure.entity.UserRoleEntity;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class UserMapper {

  // =====================================================
  // USER
  // =====================================================

  public static Optional<User> userToDomain(UserEntity entity) {
    if (entity == null) {
      return Optional.empty();
    }

    return Optional.of(
        new User(
            entity.getId(),
            entity.getName(),
            entity.getEmail(),
            entity.isEmailVerified(),
            entity.getEmailVerifiedAt(),
            userRolesToDomain(entity.getUserRoles())));
  }

  public static UserEntity userToEntity(User user) {
    if (user == null) {
      return null;
    }

    UserEntity entity = new UserEntity();
    entity.setId(user.getId());
    entity.setName(user.getName());
    entity.setEmail(user.getEmail());
    entity.setEmailVerified(user.isEmailVerified());
    entity.setEmailVerifiedAt(user.getEmailVerifiedAt());
    entity.setUserRoles(userRolesToEntity(user.getUserRoles(), entity));

    return entity;
  }

  // =====================================================
  // USER ROLE
  // =====================================================

  public static Set<UserRole> userRolesToDomain(Set<UserRoleEntity> entities) {
    if (entities == null || entities.isEmpty()) {
      return Collections.emptySet();
    }

    return entities.stream().map(UserMapper::userRoleToDomain).collect(Collectors.toSet());
  }

  public static UserRole userRoleToDomain(UserRoleEntity entity) {
    if (entity == null) {
      return null;
    }

    return new UserRole(
        entity.getId(),
        null, // tránh circular mapping User -> UserRole -> User
        roleToDomain(entity.getRole()));
  }

  public static Set<UserRoleEntity> userRolesToEntity(
      Set<UserRole> userRoles, UserEntity userEntity) {
    if (userRoles == null || userRoles.isEmpty()) {
      return Collections.emptySet();
    }

    return userRoles.stream()
        .map(userRole -> userRoleToEntity(userRole, userEntity))
        .collect(Collectors.toSet());
  }

  public static UserRoleEntity userRoleToEntity(UserRole userRole, UserEntity userEntity) {
    if (userRole == null) {
      return null;
    }

    UserRoleEntity entity = new UserRoleEntity();
    entity.setId(userRole.getId());
    entity.setUser(userEntity);
    entity.setRole(roleToEntity(userRole.getRole()));

    return entity;
  }

  // =====================================================
  // ROLE
  // =====================================================

  public static Role roleToDomain(RoleEntity entity) {
    if (entity == null) {
      return null;
    }

    return new Role(
        entity.getId(),
        entity.getName(),
        entity.getRolePermissions() == null
            ? Collections.emptySet()
            : entity.getRolePermissions().stream()
                .map(rolePermission -> rolePermission.getPermission())
                .map(permission -> new Permission(permission.getId(), permission.getCode()))
                .collect(Collectors.toSet()));
  }

  public static RoleEntity roleToEntity(Role role) {
    if (role == null) {
      return null;
    }

    RoleEntity entity = new RoleEntity();
    entity.setId(role.getId());
    entity.setName(role.getName());

    return entity;
  }

  // =====================================================
  // PERMISSION
  // =====================================================

  public static Set<Permission> permissionsToDomain(Set<PermissionEntity> entities) {
    if (entities == null || entities.isEmpty()) {
      return Collections.emptySet();
    }

    return entities.stream()
        .map(permission -> new Permission(permission.getId(), permission.getCode()))
        .collect(Collectors.toSet());
  }

  public static Set<PermissionEntity> permissionsToEntity(Set<Permission> permissions) {
    if (permissions == null || permissions.isEmpty()) {
      return Collections.emptySet();
    }

    return permissions.stream()
        .map(
            permission -> {
              PermissionEntity entity = new PermissionEntity();
              entity.setId(permission.getId());
              entity.setCode(permission.getCode());
              return entity;
            })
        .collect(Collectors.toSet());
  }
}
