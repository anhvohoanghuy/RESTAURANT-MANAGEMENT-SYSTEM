---
phase: 18-order-and-order-item-cancellation-with-compensation-release-
plan: 04
subsystem: payments
tags: [kafka, jackson-3, idempotency, refund, dlt, spring-kafka]

# Dependency graph
requires:
  - phase: 18-01
    provides: OrderCancelledEvent contract (eventId, wholeOrder, cancelledLineIds, totalLines) published to orders.cancelled
provides:
  - Payment's first-ever Kafka consumer (orders.cancelled) with dedicated DLT and Jackson-3 serde
  - Payment's first idempotency ledger (payment_processed_events)
  - PaymentAutoRefundService: whole-order-gated, idempotent auto-refund on cancellation (CANCEL-06)
  - Nullable PaymentRefundEntity.actorUserId for honest system-triggered refunds
affects: [payment_context, 18-05, 18-06, phase-18-wave-merge]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Payment-side cross-context Kafka consumer wiring (@EnableKafka + ErrorHandlingDeserializer + Jackson-3 JacksonJsonDeserializer + dedicated DLT KafkaTemplate), mirrored from TicketStatusChangedKafkaConsumerConfig"
    - "Idempotency ledger pre-check + business-logic + ledger-row-saved-last idiom, mirrored from OrderConfirmationService"
    - "Null actorUserId for system-triggered financial mutations (no fabricated user), mirrored from InventoryReservationSettlementService"

key-files:
  created:
    - src/main/java/com/example/feat1/DDD/payment_context/infrastructure/entity/PaymentProcessedEventEntity.java
    - src/main/java/com/example/feat1/DDD/payment_context/infrastructure/repository/PaymentProcessedEventRepository.java
    - src/main/java/com/example/feat1/DDD/payment_context/application/PaymentAutoRefundService.java
    - src/main/java/com/example/feat1/DDD/payment_context/infrastructure/adapter/OrderCancelledPaymentListener.java
    - src/main/java/com/example/feat1/DDD/payment_context/infrastructure/config/OrderCancelledPaymentKafkaConsumerConfig.java
    - src/test/java/com/example/feat1/DDD/payment_context/application/PaymentAutoRefundServiceTest.java
  modified:
    - src/main/java/com/example/feat1/DDD/payment_context/infrastructure/entity/PaymentRefundEntity.java

key-decisions:
  - "Reused PaymentService.recordRefund verbatim per payment rather than writing PaymentRefundEntity directly, inheriting its overpay guard and per-payment idempotency-key dedup as a second independent replay-safety layer beneath the new event ledger"
  - "Deterministic idempotency key auto-cancel-{orderId}-{paymentId} ensures recordRefund's own dedup catches any replay that slips past the ledger pre-check"
  - "Ledger row (payment_processed_events) saved LAST in the same @Transactional method, mirroring OrderConfirmationService's CR-01/I-WR-01-safe idiom, so a mid-loop failure rolls back the whole transaction rather than leaving a false processed marker"

requirements-completed: [CANCEL-06]

# Metrics
duration: 6min
completed: 2026-07-10
---

# Phase 18 Plan 04: Payment Auto-Refund on Order Cancellation Summary

**Payment's first-ever Kafka consumer and idempotency ledger auto-refund a cancelled paid whole-order by reusing PaymentService.recordRefund with a null system actor, gated strictly on wholeOrder=true (D-6), and wired with full poison-pill/DLT protection using Jackson-3.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-07-10T04:47:00Z (approx, first task commit 11:48:05+07:00)
- **Completed:** 2026-07-10T04:51:55Z
- **Tasks:** 3 completed (Task 2 was TDD: RED + GREEN)
- **Files modified:** 7 (6 created, 1 modified)

## Accomplishments
- `PaymentProcessedEventEntity` / `PaymentProcessedEventRepository`: Payment's first idempotency ledger, a structural twin of `InventoryProcessedEventEntity`, mapped to `payment_processed_events` with a unique `(event_id, consumer_name)` constraint
- `PaymentRefundEntity.actorUserId` made nullable so system-triggered refunds record an honest `null` actor rather than a fabricated user
- `PaymentAutoRefundService.onOrderCancelled`: gates strictly on `event.wholeOrder()` (D-6), pre-checks the ledger for replay, iterates every `PaymentEntity` on the order computing the unrefunded remainder, and calls the existing `PaymentService.recordRefund(null, ...)` per payment with a deterministic idempotency key — reusing (not duplicating) the overpay guard and per-payment dedup
- `OrderCancelledPaymentListener` + `OrderCancelledPaymentKafkaConsumerConfig`: Payment's first-ever `@EnableKafka` consumer wiring on `orders.cancelled`, with `ErrorHandlingDeserializer` + Jackson-3 `JacksonJsonDeserializer` (trusted-pinned to `order_context.application.event`), a `DefaultErrorHandler` (3x `FixedBackOff`, non-retryable `DeserializationException`), and a new dedicated `orderCancelledPaymentDltKafkaTemplate` using Jackson-3 `JacksonJsonSerializer`
- `PaymentAutoRefundServiceTest`: 5 tests covering every `<behavior>` bullet (partial-cancel no-op, whole-order per-payment refund with correct remainder math and idempotency key, fully-refunded-payment skip, replay idempotency via ledger, no-payments no-op-but-ledger-recorded)

## Task Commits

Each task was committed atomically:

1. **Task 1: Payment idempotency ledger + nullable refund actor** - `ee890f3` (feat)
2. **Task 2: PaymentAutoRefundService (whole-order-gated, idempotent)** - RED `341b8ae` (test) → GREEN `dc10c98` (feat) — no refactor needed, implementation was already minimal and clean
3. **Task 3: Payment's first Kafka consumer — listener + config with dedicated DLT** - `4d66d75` (feat)

**Plan metadata:** (pending — this SUMMARY commit)

## Files Created/Modified
- `PaymentProcessedEventEntity.java` - Payment's first idempotency ledger entity (`payment_processed_events`, unique `(event_id, consumer_name)`)
- `PaymentProcessedEventRepository.java` - `existsByEventIdAndConsumerName` idempotency check
- `PaymentRefundEntity.java` - `actorUserId` changed from `nullable = false` to nullable
- `PaymentAutoRefundService.java` - whole-order-gated, idempotent auto-refund iterating all payments via `PaymentService.recordRefund`
- `OrderCancelledPaymentListener.java` - thin `@KafkaListener` delegate on `orders.cancelled`
- `OrderCancelledPaymentKafkaConsumerConfig.java` - `@EnableKafka` consumer factory, error handler, dedicated DLT `KafkaTemplate`, and listener container factory
- `PaymentAutoRefundServiceTest.java` - unit test suite (Mockito) for all behavior bullets

## Decisions Made
- Reused `PaymentService.recordRefund` per payment rather than constructing `PaymentRefundEntity` directly, so the existing overpay guard (`refundExceedsPayment`) and per-payment idempotency-key dedup (`findByPayment_IdAndIdempotencyKey`) remain the single source of truth for refund correctness (RESEARCH "Don't Hand-Roll" guidance)
- Used a real `RefundResponse` record instance instead of `mock(RefundResponse.class)` in the test — records are implicitly final and Mockito's default mock maker cannot mock final classes without the inline mock maker being explicitly configured; constructing the record directly avoids a fragile dependency on mockmaker configuration
- Ledger row saved LAST in the transactional method (mirrors `OrderConfirmationService`'s `CR-01`/`I-WR-01` fix) so a concurrent-duplicate unique violation rolls back the whole transaction instead of pre-committing a "processed" marker ahead of unguaranteed refund work

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required. `payment.order-cancelled.consumer.group-id` and `order.events.order-cancelled-topic` both have sensible defaults (`payment-order-cancelled` / `orders.cancelled`) matching the Plan 01 contract; no new environment variables needed.

## Next Phase Readiness

CANCEL-06 is closed: a whole-order cancellation now automatically refunds every payment on the order via Payment's first Kafka consumer, with idempotency guaranteed by two independent layers (the new `payment_processed_events` ledger by `eventId`, and `recordRefund`'s own per-payment idempotency-key dedup). Verified via full-context Spring Boot test run: the new `payment-order-cancelled` consumer group registers and attempts to bootstrap against Kafka alongside all other existing consumer groups (`order-ticket-status-changed`, `kitchen-order-confirmed`, `order-stock-result`, `inventory-order-created`, `inventory-settlement`) with zero bean-name collisions and zero context-load errors. No blockers for wave merge or downstream Plan 05/06 work — `payment_context` files touched here are disjoint from Plans 02/03's inventory/order files per the plan's stated parallelism rationale.

---
*Phase: 18-order-and-order-item-cancellation-with-compensation-release-*
*Completed: 2026-07-10*

## Self-Check: PASSED

All 7 created/modified source files verified present on disk. All 5 task/summary commit hashes (`ee890f3`, `341b8ae`, `dc10c98`, `4d66d75`, `2a11fd0`) verified present in git log.
