package com.example.feat1.DDD.identity_context.infastructure.entity;

import jakarta.persistence.*;
import java.util.Set;
import java.util.UUID;
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
@Table(name = "permissions")
public class PermissionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @ToString.Include
  private UUID id;

  @Column(nullable = false, unique = true)
  @ToString.Include
  private String code;

  @OneToMany(mappedBy = "permission", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<RolePermissionEntity> rolePermissions;
}
