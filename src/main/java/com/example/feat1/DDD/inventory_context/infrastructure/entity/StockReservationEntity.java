package com.example.feat1.DDD.inventory_context.infrastructure.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A per-order stock reservation. Created when an OrderCreated event successfully reserves stock;
 * the unique {@code order_id} guards against replayed events double-reserving. Each reservation
 * records the reserved base quantity per ingredient so Phase 16 can settle the hold into an actual
 * deduction (reserved -> on_hand).
 */
@Getter
@Setter
@Entity
@Table(
    name = "stock_reservations",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_stock_reservation_order",
            columnNames = {"order_id"}))
public class StockReservationEntity {

  /**
   * Lifecycle of a reservation. Created as HELD; settled to SETTLED once the order deducts, or
   * released to RELEASED once every line is cancelled without settling (a mixed settled/released
   * order still flips to SETTLED — RELEASED only applies to the pure whole-order-cancel case).
   */
  public enum ReservationStatus {
    HELD,
    SETTLED,
    RELEASED
  }

  /** Reserved base quantity for a single ingredient within a reservation. */
  @Embeddable
  @Getter
  @Setter
  public static class ReservationLine {
    @Column(name = "ingredient_id", nullable = false)
    private UUID ingredientId;

    @Column(name = "reserved_quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal reservedQuantity;

    public ReservationLine() {}

    public ReservationLine(UUID ingredientId, BigDecimal reservedQuantity) {
      this.ingredientId = ingredientId;
      this.reservedQuantity = reservedQuantity;
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ReservationStatus status = ReservationStatus.HELD;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "stock_reservation_lines",
      joinColumns = @JoinColumn(name = "reservation_id", nullable = false))
  private List<ReservationLine> lines = new ArrayList<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /**
   * Builds a HELD reservation for an order with one line per reserved ingredient.
   *
   * @param orderId the order this reservation belongs to (unique)
   * @param reservedByIngredient reserved base quantity keyed by ingredient id
   */
  public static StockReservationEntity held(
      UUID orderId, Map<UUID, BigDecimal> reservedByIngredient) {
    StockReservationEntity reservation = new StockReservationEntity();
    reservation.setOrderId(orderId);
    reservation.setStatus(ReservationStatus.HELD);
    reservation.setCreatedAt(Instant.now());
    List<ReservationLine> lines = new ArrayList<>();
    if (reservedByIngredient != null) {
      reservedByIngredient.forEach(
          (ingredientId, quantity) -> lines.add(new ReservationLine(ingredientId, quantity)));
    }
    reservation.setLines(lines);
    return reservation;
  }
}
