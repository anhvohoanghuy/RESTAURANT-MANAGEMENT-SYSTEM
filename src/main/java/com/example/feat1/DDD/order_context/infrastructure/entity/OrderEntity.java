package com.example.feat1.DDD.order_context.infrastructure.entity;

import com.example.feat1.DDD.order_context.domain.model.OrderStatus;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "orders")
public class OrderEntity {

  /**
   * Single source of truth for the persisted rejection-reason cap (IN-03 / T-17.1-18 / I-WR-04).
   * {@link com.example.feat1.DDD.order_context.application.OrderConfirmationService#MAX_REASON_LEN}
   * derives from this same constant so the truncation cap and the column length can never drift
   * apart.
   */
  public static final int REJECTION_REASON_MAX_LEN = 60000;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderStatus status = OrderStatus.PENDING_CONFIRMATION;

  @Column(name = "rejection_reason", length = REJECTION_REASON_MAX_LEN)
  private String rejectionReason;

  @Column(name = "submitted_at", nullable = false)
  private Instant submittedAt;

  @Column(name = "table_id", nullable = false)
  private UUID tableId;

  @Column(name = "table_session_id")
  private UUID tableSessionId;

  @Column(name = "table_code", nullable = false)
  private String tableCode;

  @Column(name = "table_name", nullable = false)
  private String tableName;

  @Column(name = "area_id", nullable = false)
  private UUID areaId;

  @Column(name = "area_name", nullable = false)
  private String areaName;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal total = BigDecimal.ZERO;

  @OneToMany(
      mappedBy = "order",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<OrderLineEntity> lines = new ArrayList<>();
}
