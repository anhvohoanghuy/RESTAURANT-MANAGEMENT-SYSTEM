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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Stock-on-hand for an ingredient in the default location. Quantity is stored in the ingredient's
 * base unit so balances can be compared and reconstructed deterministically.
 */
@Getter
@Setter
@Entity
@Table(
    name = "inventory_stock_balances",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_stock_balance_ingredient_location",
            columnNames = {"ingredient_id", "location_code"}))
public class InventoryStockBalanceEntity {
  public static final String DEFAULT_LOCATION = "DEFAULT";

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ingredient_id", nullable = false)
  private IngredientEntity ingredient;

  @Column(name = "location_code", nullable = false)
  private String locationCode = DEFAULT_LOCATION;

  @Column(name = "quantity_on_hand", nullable = false, precision = 18, scale = 6)
  private BigDecimal quantityOnHand = BigDecimal.ZERO;

  /**
   * Running total of stock held by open reservations (base unit). Available stock is computed as
   * {@code quantityOnHand - reservedQuantity} by the reservation service; it is never stored.
   */
  @Column(name = "reserved_quantity", nullable = false, precision = 18, scale = 6)
  private BigDecimal reservedQuantity = BigDecimal.ZERO;

  @Column(name = "base_unit", nullable = false)
  private String baseUnit;

  @Column(name = "low_stock_threshold", precision = 18, scale = 6)
  private BigDecimal lowStockThreshold;

  @Column(name = "last_movement_at")
  private Instant lastMovementAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;
}
