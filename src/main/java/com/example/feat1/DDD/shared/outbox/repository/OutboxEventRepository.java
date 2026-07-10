package com.example.feat1.DDD.shared.outbox.repository;

import com.example.feat1.DDD.shared.outbox.entity.OutboxEventEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

  /**
   * Claims up to {@code batch} PENDING rows for exclusive publish by this instance. {@code FOR
   * UPDATE SKIP LOCKED} lets concurrent app instances poll the same table without double-publishing
   * a row — MySQL 8+ only; this native query MUST NOT execute against H2 (no SKIP LOCKED support),
   * which is why {@code OutboxRelay} is disabled in the test profile.
   */
  @Query(
      value =
          """
          SELECT * FROM outbox_events
          WHERE status = 'PENDING'
          ORDER BY created_at
          LIMIT :batch
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<OutboxEventEntity> claimPending(@Param("batch") int batch);

  /** Portable finder for tests/fallback — does not rely on SKIP LOCKED. */
  List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(String status);

  /**
   * Bulk-deletes rows matching {@code status} whose {@code createdAt} is strictly before {@code
   * cutoff} (IN-02 retention sweep, T-17.2-05). {@code createdAt} — not {@code sentAt} — is used as
   * the cutoff basis because it is set at row-creation time and is therefore always populated and
   * monotonic, whereas {@code sentAt} is only set once a row is SENT. Filtered strictly on {@code
   * status} so PENDING/FAILED rows are never deleted regardless of age (T-17.2-07). Returns the
   * number of rows removed.
   */
  @Modifying
  @Query("DELETE FROM OutboxEventEntity e WHERE e.status = :status AND e.createdAt < :cutoff")
  int deleteByStatusAndCreatedAtBefore(
      @Param("status") String status, @Param("cutoff") Instant cutoff);

  /** Counts rows in the given status — used to surface FAILED (poison) rows (IN-02). */
  long countByStatus(String status);
}
