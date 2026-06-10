package com.example.feat1.DDD.identity_context.infastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "credentials")
public class CredentialEntity {

  @Id private UUID id;

  private UUID userId;

  @Column(nullable = false, unique = true)
  private String providerUserId;

  private String passwordHash;

  private String authProvider;
}
