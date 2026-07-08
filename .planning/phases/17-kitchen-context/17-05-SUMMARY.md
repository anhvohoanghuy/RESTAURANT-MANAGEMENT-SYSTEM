---
phase: 17-kitchen-context
plan: 05
subsystem: kitchen_context/application
tags: [kitchen, fulfillment, forward-only-transition, exactly-once, kafka, settle-trigger]
dependency-graph:
  requires: [17-02, 17-04]
  provides: [KitchenTicketAdvanceService, AdvanceItemStatusRequest, KitchenItemResponse]
  affects: [17-06, 17-07]
tech-stack:
  added: []
  patterns:
    - "lock-then-check-then-mutate-then-publish (row lock acquired before the forward-only guard)"
    - "single-step forward-only switch guard (TableOperationService.isValidTransition analog)"
    - "publishAfterCommit via TransactionSynchronizationManager.registerSynchronization (OrderSubmissionService analog)"
key-files:
  created:
    - src/main/java/com/example/feat1/DDD/kitchen_context/application/dto/AdvanceItemStatusRequest.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/application/dto/KitchenItemResponse.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketAdvanceService.java
    - src/test/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketAdvanceServiceTest.java
  modified: []
decisions:
  - "isValidTransition modeled as a strict per-status switch (one legal next value per case, COMPLETED terminal) rather than a rank/ordinal comparison, matching TableOperationService's exact single-step chain shape and the plan's explicit case-by-case wording."
  - "Both SettleTriggerEvent and KitchenTicketStatusChangedEvent use independent private publishAfterCommit helpers (one per publisher port) rather than a shared generic helper, mirroring the plan's 'one private helper per event type' option in 17-PATTERNS.md."
metrics:
  duration: "~40 minutes"
  completed: 2026-07-08
---

# Phase 17 Plan 05: Kitchen Ticket Advance Service Summary

Implemented the core fulfillment use case — `KitchenTicketAdvanceService.advance(orderId, itemId,
request, actorId)` — that locks the item row via the dual-key `lockByOrderIdAndItemId`
(`PESSIMISTIC_WRITE`) BEFORE checking the forward-only transition, enforces a strict single-step
`QUEUED→PREPARING→READY→SERVED→COMPLETED` chain, and publishes both the `SettleTriggerEvent`
(exactly once, only on `QUEUED→PREPARING`) and the `KitchenTicketStatusChangedEvent` (every valid
transition) after commit via `TransactionSynchronizationManager.registerSynchronization`.

## What Was Built

**Task 1 — DTOs:**
- `AdvanceItemStatusRequest(KitchenItemStatus targetStatus)` — PATCH request body record.
- `KitchenItemResponse(itemId, orderId, orderLineId, dishName, quantity, status)` — item view
  returned after a successful advance.

**Task 2 — `KitchenTicketAdvanceService` (TDD RED→GREEN):**
- `@Transactional advance(UUID orderId, UUID itemId, AdvanceItemStatusRequest request, UUID actorId)`:
  1. `itemRepository.lockByOrderIdAndItemId(orderId, itemId)` — dual-key pessimistic lock acquired
     FIRST, before any status check (closes T-17-10 double-settle race and T-17-12 IDOR: an item
     belonging to a different order is simply not found by this query).
  2. `isValidTransition(current, target)` — a `switch` allowing only the immediate next status per
     case; `COMPLETED` is terminal (always `false`). Illegal transitions throw
     `KitchenDomainException.transitionInvalid()` with zero mutation.
  3. On a valid transition: `item.setStatus(target)`, `itemRepository.save(item)`.
  4. If `current == QUEUED && target == PREPARING`: build and after-commit-publish a
     `SettleTriggerEvent(eventId, TYPE, occurredAt, orderId, item.getOrderLineId(),
     ticket.getItems().size())` via `KitchenSettleTriggerPublisher`.
  5. Always after-commit-publish a `KitchenTicketStatusChangedEvent` built from the ticket's full
     current per-item status snapshot via `KitchenTicketStatusChangedPublisher`.
  6. Return a mapped `KitchenItemResponse`.
- Both publishes use a private `publishAfterCommit`-style helper per event type
  (`publishSettleTriggerAfterCommit` / `publishStatusChangedAfterCommit`), copied from the
  `OrderSubmissionService`/`TableOperationService` `TransactionSynchronizationManager` pattern —
  never a synchronous mid-transaction publish (closes T-17-13).

**Test coverage (`KitchenTicketAdvanceServiceTest`, 9 tests, all green):**
- `QUEUED→PREPARING`: status persisted, `SettleTriggerEvent` published exactly once with correct
  `orderId`/`orderLineId`/`totalLines`/`eventType`, `KitchenTicketStatusChangedEvent` published once.
- `PREPARING→READY`, `READY→SERVED`, `SERVED→COMPLETED`: status-changed published, settle-trigger
  NEVER published.
- Skip (`QUEUED→READY`), revert (`READY→PREPARING`), and advance-from-terminal (`COMPLETED→COMPLETED`)
  all throw `transitionInvalid()`, leave status unchanged, and publish nothing.
- Item not found for the given `(orderId, itemId)` throws `itemNotFound()`.
- Concurrent double-advance: first call advances `QUEUED→PREPARING` and publishes once; a second
  call simulating the re-read of the now-`PREPARING` row rejects as `transitionInvalid()`, so the
  settle-trigger is verified published exactly once (`times(1)`) across both calls.

## Verification

- `./mvnw -o test -Dtest=KitchenTicketAdvanceServiceTest` → 9/9 passed.
- `grep -n "lockByOrderIdAndItemId\|isValidTransition\|registerSynchronization" KitchenTicketAdvanceService.java`
  confirms the lock call (line 45) precedes the `isValidTransition` guard (line 50), and both
  publish helpers use `registerSynchronization` (lines 114, 128).
- Full suite: `./mvnw -o clean test` → 176 tests, 0 failures, 0 errors, BUILD SUCCESS.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Mocked `save()` stub returned `null`, causing NPEs in the RED→GREEN test run**
- **Found during:** Task 2, first GREEN test run.
- **Issue:** `KitchenTicketItemRepository` is a Mockito mock; an unstubbed `save(item)` call
  returns `null` by default, unlike a real JPA repository which returns the same managed entity.
  The service's `advance` method used the return value of `save(item)` for subsequent field reads
  (`saved.getOrderLineId()`, `saved.getTicket()`), causing `NullPointerException` in 5 of 9 tests.
- **Fix:** Added `when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));`
  to the test's `@BeforeEach` so the mock echoes its argument, matching real JPA `save()` semantics.
- **Files modified:** `src/test/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketAdvanceServiceTest.java`
- **Commit:** e30d663 (folded into the GREEN commit, same test file already touched by the RED commit c4f63e2)

No other deviations — the plan's `<action>` and `<behavior>` specs were followed as written; the
`isValidTransition` switch, dual-key lock, and both publish points match the plan's interfaces and
threat-model mitigations exactly.

## TDD Gate Compliance

RED gate: commit `c4f63e2` (`test(17-05): add failing test for KitchenTicketAdvanceService`) — test
file added referencing a nonexistent `KitchenTicketAdvanceService`, confirmed failing to compile
before any implementation existed.
GREEN gate: commit `e30d663` (`feat(17-05): implement KitchenTicketAdvanceService`) — implementation
added, all 9 tests pass.
No REFACTOR commit was needed; the implementation matched the target shape on first GREEN pass.

## Self-Check: PASSED

All created files confirmed present on disk; all three task commits (4d2e738, c4f63e2, e30d663)
confirmed present in git log.
