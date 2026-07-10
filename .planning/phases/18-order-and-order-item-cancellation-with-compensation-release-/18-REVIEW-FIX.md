---
phase: 18-order-and-order-item-cancellation-with-compensation-release-
fixed_at: 2026-07-10T12:31:00Z
review_path: .planning/phases/18-order-and-order-item-cancellation-with-compensation-release-/18-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 18: Code Review Fix Report

**Fixed at:** 2026-07-10T12:31:00Z
**Source review:** .planning/phases/18-order-and-order-item-cancellation-with-compensation-release-/18-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 4 (3 critical/blocker + WR-01, user-selected subset of the review's 3 critical / 2 warning / 2 info findings)
- Fixed: 4
- Skipped: 0

Each fix was verified with a dedicated regression test proven to fail (NPE / wrong assertion) against
the pre-fix code and pass after the fix, then committed atomically. The full Maven suite was run once
at the end: **257 tests, 0 failures, 0 errors, 0 skipped (BUILD SUCCESS)** — the pre-existing 252 tests
plus 5 new regression tests (2 for CR-01, 1 each for CR-02/CR-03/WR-01).

## Fixed Issues

### CR-01: NullPointerException in KitchenStatusProjectionService when a ticket snapshot includes a CANCELLED item

**Files modified:** `src/main/java/com/example/feat1/DDD/order_context/application/KitchenStatusProjectionService.java`, `src/test/java/com/example/feat1/DDD/order_context/application/KitchenStatusProjectionServiceTest.java`
**Commit:** `48ab24c`
**Applied fix:** `deriveTargetStatus` now filters `KitchenItemStatus.CANCELLED` items out of the per-ticket
snapshot before any rank comparison runs, so a voided item never participates in the "all
served"/"all ready"/"all completed" aggregates and never blocks a still-progressing sibling's
advance. If every item on the ticket is cancelled, the method returns `null` (no projection).
`itemRank` was additionally hardened with `getOrDefault(status, -1)` instead of `get(status)` as a
defense-in-depth fail-safe against any future unmapped `KitchenItemStatus`.
Added two regression tests: a `CANCELLED` item mixed with a `READY` sibling now derives `READY`
without throwing (previously NPE'd at `itemRank(CANCELLED)` unboxing), and an all-`CANCELLED`
snapshot leaves the order status untouched.

### CR-02: Whole-order cancel forces terminal CANCELLED + wholeOrder=true even when a line was excluded as already-preparing

**Files modified:** `src/main/java/com/example/feat1/DDD/order_context/application/OrderCancellationService.java`, `src/test/java/com/example/feat1/DDD/order_context/application/OrderCancellationServiceTest.java`
**Commit:** `7ca8fad`
**Applied fix:** `applyCancellation` now computes line eligibility (the kitchen-port
before-preparing race-guard check) *before* mutating any line or the order. For the whole-order
path, if any active candidate line was excluded because its kitchen item already reached
`PREPARING`, the method throws `OrderDomainException.cancelWindowClosed()` with **no** line
mutation, **no** order-status mutation, and **no** outbox publish — closing the gap where
`PaymentAutoRefundService` would otherwise issue a full refund (`wholeOrder=true`) for food still
being actively prepared. The partial-cancel path's behavior (per-line exclusion, `noCancellableLines()`
when nothing is left cancellable) is unchanged.
Added a regression test: a whole-order cancel where one of two lines is already
`AT_OR_AFTER_PREPARING` now throws `cancelWindowClosed`, leaves the order at `CONFIRMED` with
neither line cancelled, and never invokes the outbox writer.

### CR-03: InventoryMovementType.RESERVATION_RELEASE not classified — manual movement endpoint silently corrupts stock-on-hand

**Files modified:** `src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryStockService.java`, `src/test/java/com/example/feat1/DDD/inventory_context/application/InventoryStockServiceTest.java`
**Commit:** `bd3addf`
**Applied fix:** `recordMovement`'s dispatch is now explicit and fail-closed: the previous catch-all
`else` (treated as an absolute `STOCK_COUNT` set) is now gated on `type.isCount()`, and a new final
`else` throws `InventoryDomainException.movementInvalid(...)` for any movement type that is neither
inbound, outbound, nor a count. `RESERVATION_RELEASE` (audit-only; must only ever decrement
`reservedQuantity`, never `quantityOnHand`) is now rejected via the manual staff endpoint instead of
silently overwriting `quantityOnHand`. Per the prescribed fix, `InventoryMovementType`'s
`isInbound()`/`isOutbound()`/`isCount()` predicates were deliberately left unchanged
(`RESERVATION_RELEASE` must NOT be added to `isOutbound()`, as it must never decrement on-hand).
Added a regression test: submitting a `RESERVATION_RELEASE` manual movement now throws
`movementInvalid` (`MOVEMENT_INVALID` code) and leaves `quantityOnHand` untouched, instead of
overwriting it via the old `STOCK_COUNT` fallthrough.

### WR-01: InventoryReservationReleaseService does not null-guard event.cancelledLineIds() before use

**Files modified:** `src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationReleaseService.java`, `src/test/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationReleaseServiceTest.java`
**Commit:** `d0acc71`
**Applied fix:** `onOrderCancelled` now treats a `null` `event.cancelledLineIds()` as an empty list
(`event.cancelledLineIds() == null ? List.of() : event.cancelledLineIds()`), mirroring
`KitchenTicketInvalidationService`'s existing guard on the same event field. Previously, a null value
(possible on a poison/schema-drifted payload, since the field is nullable by the record's own type)
threw an NPE on `cancelledLineIds.isEmpty()` before any idempotency/locking logic ran.
Added a regression test: an event with `cancelledLineIds = null` no longer throws and resolves the
reservation to `RELEASED` without iterating any lines.

## Skipped Issues

None — all four in-scope findings were fixed.

## Full Test Suite Result

```
./mvnw -q test
EXIT: 0 (BUILD SUCCESS)
Total Tests: 257, Failures: 0, Errors: 0, Skipped: 0
```

(257 = 252 pre-existing + 5 new regression tests: 2 for CR-01, 1 each for CR-02/CR-03/WR-01.)

## Not In Scope (left untouched per instructions)

The review also identified WR-02 (test fixtures assuming an unenforced `wholeOrder` invariant — now
closed by CR-02's fix, though WR-02 itself was not separately actioned as a distinct finding),
IN-01 (dead `lineNotCancellable()` code), and IN-02 (Javadoc consistency note). None of these were
requested for this fix pass and were left untouched.

---

_Fixed: 2026-07-10T12:31:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
