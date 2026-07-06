package com.example.feat1.DDD.inventory_context.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "inventory_ingredient_costs")
public class IngredientCostEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ingredient_id", nullable = false)
  private IngredientEntity ingredient;

  @Column(name = "unit_cost", nullable = false, precision = 12, scale = 4)
  private BigDecimal unitCost;

  @Column(name = "cost_unit", nullable = false)
  private String costUnit;

  @Column(name = "effective_at", nullable = false)
  private Instant effectiveAt;

  private String source;

  @Column(length = 2000)
  private String note;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
