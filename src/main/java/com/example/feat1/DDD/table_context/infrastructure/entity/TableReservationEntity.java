package com.example.feat1.DDD.table_context.infrastructure.entity;

import com.example.feat1.DDD.table_context.domain.model.ReservationStatus;
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
@Table(name = "table_reservations")
public class TableReservationEntity {
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

  @Column(name = "customer_name", nullable = false)
  private String customerName;

  @Column(name = "customer_phone", nullable = false)
  private String customerPhone;

  @Column(name = "customer_email")
  private String customerEmail;

  @Column(name = "party_size", nullable = false)
  private int partySize;

  @Column(name = "start_time", nullable = false)
  private Instant startTime;

  @Column(name = "end_time", nullable = false)
  private Instant endTime;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReservationStatus status = ReservationStatus.PENDING;

  private String note;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_by")
  private UUID updatedBy;

  @Column(name = "updated_at")
  private Instant updatedAt;
}
