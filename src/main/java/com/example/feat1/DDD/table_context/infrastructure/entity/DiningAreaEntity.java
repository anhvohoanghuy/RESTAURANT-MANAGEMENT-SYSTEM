package com.example.feat1.DDD.table_context.infrastructure.entity;

import com.example.feat1.DDD.table_context.domain.model.TableStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "dining_areas")
public class DiningAreaEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TableStatus status = TableStatus.ACTIVE;
}
