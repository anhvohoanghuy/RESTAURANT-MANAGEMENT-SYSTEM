package com.example.feat1.DDD.order_context.infrastructure.entity;

import com.example.feat1.DDD.order_context.domain.model.CartStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "order_carts")
public class OrderCartEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CartStatus status = CartStatus.ACTIVE;

  @Column(name = "table_id")
  private UUID tableId;

  @Column(name = "table_session_id")
  private UUID tableSessionId;

  @Column(name = "table_code")
  private String tableCode;

  @Column(name = "table_name")
  private String tableName;

  @Column(name = "area_id")
  private UUID areaId;

  @Column(name = "area_name")
  private String areaName;

  @OneToMany(
      mappedBy = "cart",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<OrderCartLineEntity> lines = new ArrayList<>();
}
