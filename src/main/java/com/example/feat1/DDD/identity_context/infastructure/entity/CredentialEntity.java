package com.example.feat1.DDD.identity_context.infastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "credentials")
public class CredentialEntity {

  @Id @ToString.Include private UUID id;

  @ToString.Include private UUID userId;

  @Column(nullable = false, unique = true)
  @ToString.Include
  private String providerUserId;

  private String passwordHash;

  @ToString.Include private String authProvider;
}
