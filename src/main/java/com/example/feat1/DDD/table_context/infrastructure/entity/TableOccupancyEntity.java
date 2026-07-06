package com.example.feat1.DDD.table_context.infrastructure.entity;

import com.example.feat1.DDD.table_context.domain.model.TableOccupancyState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "table_occupancy")
public class TableOccupancyEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "table_id", nullable = false, unique = true)
  private UUID tableId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TableOccupancyState state = TableOccupancyState.AVAILABLE;

  private String reason;

  @Column(name = "updated_by", nullable = false)
  private UUID updatedBy;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
