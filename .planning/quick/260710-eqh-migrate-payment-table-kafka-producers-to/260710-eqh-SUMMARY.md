---
phase: quick-260710-eqh
plan: 01
subsystem: infra
tags: [kafka, jackson, spring-kafka, outbox, javadoc]

# Dependency graph
requires:
  - phase: 17.2
    provides: Outbox durability (transactional outbox + relay) and I-WR-05 serializer migration decision
provides:
  - Payment and Table Kafka producers migrated from Jackson-2 JsonSerializer to Jackson-3 JacksonJsonSerializer (fixes latent Instant serialization failure)
  - Corrected OutboxEventRepository.claimPending and OutboxRelay Javadoc (no longer falsely claims cross-instance no-double-publish safety)
affects: [payment_context, table_context, shared.outbox]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "All Kafka producer configs across contexts now consistently use Jackson-3 JacksonJsonSerializer for the value serializer (StringSerializer for keys), matching KitchenSettleTriggerProducerConfig / InventoryKafkaProducerConfig."

key-files:
  created: []
  modified:
    - src/main/java/com/example/feat1/DDD/payment_context/infrastructure/config/PaymentKafkaProducerConfig.java
    - src/main/java/com/example/feat1/DDD/table_context/infrastructure/config/TableKafkaProducerConfig.java
    - src/main/java/com/example/feat1/DDD/shared/outbox/repository/OutboxEventRepository.java
    - src/main/java/com/example/feat1/DDD/shared/outbox/infrastructure/OutboxRelay.java

key-decisions: []

patterns-established: []

requirements-completed: [15-01, WR-01]

# Metrics
duration: ~12min
completed: 2026-07-10
---

# Quick Task 260710-eqh: Migrate Payment/Table Kafka Producers to Jackson-3 Summary

**Payment and Table Kafka producers now use the Jackson-3 `JacksonJsonSerializer` (fixing a latent `Instant`-field serialization bug on the Boot 4 / Jackson 3 classpath), and the outbox `claimPending`/`OutboxRelay` Javadoc no longer overstates SKIP LOCKED's cross-instance safety guarantees.**

## Performance

- **Duration:** ~12 min
- **Completed:** 2026-07-10T03:41:37Z
- **Tasks:** 2/2 completed
- **Files modified:** 4

## Accomplishments
- `PaymentKafkaProducerConfig` and `TableKafkaProducerConfig` swapped the Jackson-2 `JsonSerializer` import/config for Jackson-3 `JacksonJsonSerializer`, mirroring the already-correct `KitchenSettleTriggerProducerConfig` pattern. Key serializer (`StringSerializer`) untouched.
- `OutboxEventRepository.claimPending` Javadoc rewritten to accurately describe SKIP LOCKED as a lock-contention reducer within a single poll, not a cross-instance no-double-publish guarantee, and to state the current single-instance topology plus the idempotent `order_processed_events` consumer ledger as the real duplicate-publish bound.
- `OutboxRelay` class Javadoc extended with a consistent single-instance-topology note, keeping the existing accurate WR-02 explanation of non-transactional `poll()` and per-row commits intact.
- Full Maven suite run and confirmed green: 213 tests, 0 failures, 0 errors.

## Task Commits

Each task was committed atomically:

1. **Task 1: Apply both fixes (serializer swap + Javadoc corrections)** - `ab09ab1` (fix)
2. **Task 2: Compile and run full Maven suite** - verification only, no code change; folded into Task 1 commit's verification gate (suite passed: 213/213, 0 failures, 0 errors)

**Plan metadata:** commit pending by orchestrator (docs: complete plan)

## Files Created/Modified
- `src/main/java/com/example/feat1/DDD/payment_context/infrastructure/config/PaymentKafkaProducerConfig.java` - Value serializer swapped to `JacksonJsonSerializer`
- `src/main/java/com/example/feat1/DDD/table_context/infrastructure/config/TableKafkaProducerConfig.java` - Value serializer swapped to `JacksonJsonSerializer`
- `src/main/java/com/example/feat1/DDD/shared/outbox/repository/OutboxEventRepository.java` - `claimPending` Javadoc corrected (doc-only, no query/schema/signature change)
- `src/main/java/com/example/feat1/DDD/shared/outbox/infrastructure/OutboxRelay.java` - Class Javadoc corrected for consistency (doc-only, no runtime logic change)

## Decisions Made
None - followed plan as specified.

## Deviations from Plan

None - plan executed exactly as written. The formatting of the `OutboxEventRepository.claimPending` Javadoc comment was subsequently auto-reflowed by the editor/linter to fit line-length conventions; content and meaning are unchanged from what was written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Both outstanding 17.2-review-adjacent issues (15-01 latent Instant serialization bug, WR-01 stale Javadoc) are now closed.
- Full Maven suite green (213 tests, 0 failures, 0 errors) confirms no regressions from either fix.
- No blockers for subsequent phases.

---
*Phase: quick-260710-eqh*
*Completed: 2026-07-10*

## Self-Check: PASSED

All 4 modified source files and the SUMMARY.md were verified present on disk; commit `ab09ab1` verified present in `git log --oneline --all`.
