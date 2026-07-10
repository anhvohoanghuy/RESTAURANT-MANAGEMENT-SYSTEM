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
   * Claims up to {@code batch} PENDING rows, ordered oldest-first. {@code FOR UPDATE SKIP LOCKED}
   * only reduces lock contention within a single poll — it lets this query skip rows already
   * row-locked by another in-flight transaction rather than blocking on them. Because {@code
   * poll()} is deliberately not {@code @Transactional} (WR-02), each claimed row's lock releases as
   * soon as this method returns and the row is still {@code PENDING} until its own publish
   * transaction flips it to {@code SENT}; SKIP LOCKED therefore does NOT guarantee cross-instance
   * no-double-publish safety. The current topology is single-instance ({@code poll()} serialized by
   * a single {@code @Scheduled(fixedDelay)} poller), so double-claim cannot occur in practice
   * today; if a second instance is ever introduced, at-least-once delivery plus the idempotent
   * {@code order_processed_events} consumer ledger is what bounds the impact of any duplicate
   * publish, not this query. MySQL 8+ only; this native query MUST NOT execute against H2 (no SKIP
   * LOCKED support), which is why {@code OutboxRelay} is disabled in the test profile.
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
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM OutboxEventEntity e WHERE e.status = :status AND e.createdAt < :cutoff")
  int deleteByStatusAndCreatedAtBefore(
      @Param("status") String status, @Param("cutoff") Instant cutoff);

  /** Counts rows in the given status — used to surface FAILED (poison) rows (IN-02). */
  long countByStatus(String status);
}
