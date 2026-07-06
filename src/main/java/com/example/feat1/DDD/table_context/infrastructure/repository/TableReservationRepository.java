package com.example.feat1.DDD.table_context.infrastructure.repository;

import com.example.feat1.DDD.table_context.domain.model.ReservationStatus;
import com.example.feat1.DDD.table_context.infrastructure.entity.TableReservationEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TableReservationRepository extends JpaRepository<TableReservationEntity, UUID> {
  @Query(
      """
      select r from TableReservationEntity r
      where r.tableId = :tableId
        and r.status in :statuses
        and r.startTime < :endTime
        and r.endTime > :startTime
      """)
  List<TableReservationEntity> findOverlapping(
      UUID tableId, Instant startTime, Instant endTime, Collection<ReservationStatus> statuses);

  @Query(
      """
      select r from TableReservationEntity r
      where r.tableId in :tableIds
        and r.status in :statuses
        and r.startTime < :endTime
        and r.endTime > :startTime
      """)
  List<TableReservationEntity> findOverlappingForTables(
      Collection<UUID> tableIds,
      Instant startTime,
      Instant endTime,
      Collection<ReservationStatus> statuses);
}
