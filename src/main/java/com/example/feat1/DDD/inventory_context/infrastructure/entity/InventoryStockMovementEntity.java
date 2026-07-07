package com.example.feat1.DDD.inventory_context.infrastructure.entity;

import com.example.feat1.DDD.inventory_context.domain.model.InventoryMovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * Immutable audit record of a single stock movement. Rows are never updated after creation;
 * corrections are represented as additional movements. The signed base delta allows the current
 * balance to be reconstructed by summing deltas per ingredient.
 */
@Getter
@Setter
@Entity
@Table(name = "inventory_stock_movements")
public class InventoryStockMovementEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ingredient_id", nullable = false)
  private IngredientEntity ingredient;

  @Column(name = "location_code", nullable = false)
  private String locationCode = InventoryStockBalanceEntity.DEFAULT_LOCATION;

  @Enumerated(EnumType.STRING)
  @Column(name = "movement_type", nullable = false)
  private InventoryMovementType movementType;

  /** Quantity as entered by the operator, in the entered unit (always positive). */
  @Column(name = "quantity", nullable = false, precision = 18, scale = 6)
  private BigDecimal quantity;

  @Column(name = "unit", nullable = false)
  private String unit;

  /** Signed effect on stock-on-hand, converted to the ingredient base unit. */
  @Column(name = "base_quantity_delta", nullable = false, precision = 18, scale = 6)
  private BigDecimal baseQuantityDelta;

  @Column(name = "base_unit", nullable = false)
  private String baseUnit;

  /** Balance-on-hand (base unit) after this movement was applied. */
  @Column(name = "resulting_balance", nullable = false, precision = 18, scale = 6)
  private BigDecimal resultingBalance;

  @Column(length = 2000)
  private String note;

  @Column(name = "reference_type")
  private String referenceType;

  @Column(name = "reference_id")
  private UUID referenceId;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
