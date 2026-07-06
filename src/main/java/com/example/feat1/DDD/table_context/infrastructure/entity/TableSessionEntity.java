package com.example.feat1.DDD.table_context.infrastructure.entity;

import com.example.feat1.DDD.table_context.domain.model.TableSessionStatus;
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
@Table(name = "table_sessions")
public class TableSessionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "table_id", nullable = false)
  private UUID tableId;

  @Column(name = "table_code", nullable = false)
  private String tableCode;

  @Column(name = "table_name", nullable = false)
  private String tableName;

  @Column(name = "area_id", nullable = false)
  private UUID areaId;

  @Column(name = "area_name", nullable = false)
  private String areaName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TableSessionStatus status = TableSessionStatus.OPEN;

  @Column(name = "party_size")
  private Integer partySize;

  private String note;

  @Column(name = "reservation_id")
  private UUID reservationId;

  @Column(name = "opened_at", nullable = false)
  private Instant openedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "opened_by", nullable = false)
  private UUID openedBy;

  @Column(name = "closed_by")
  private UUID closedBy;

  @Column(name = "cancelled_by")
  private UUID cancelledBy;
}
