---
phase: 18-order-and-order-item-cancellation-with-compensation-release-
reviewed: 2026-07-10T05:20:29Z
depth: deep
files_reviewed: 41
files_reviewed_list:
  - src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationReleaseService.java
  - src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationSettlementService.java
  - src/main/java/com/example/feat1/DDD/inventory_context/domain/model/InventoryDomainException.java
  - src/main/java/com/example/feat1/DDD/inventory_context/domain/model/InventoryMovementType.java
  - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/adapter/OrderCancelledInventoryListener.java
  - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/OrderCancelledKafkaConsumerConfig.java
  - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/InventoryLineReleaseEntity.java
  - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/StockReservationEntity.java
  - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/repository/InventoryLineReleaseRepository.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketAdvanceService.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketInvalidationService.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/domain/model/KitchenItemStatus.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/adapter/KitchenItemStatusAdapter.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/adapter/OrderCancelledKitchenListener.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/OrderCancelledKitchenKafkaConsumerConfig.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/repository/KitchenTicketItemRepository.java
  - src/main/java/com/example/feat1/DDD/order_context/application/KitchenStatusProjectionService.java
  - src/main/java/com/example/feat1/DDD/order_context/application/OrderCancellationService.java
  - src/main/java/com/example/feat1/DDD/order_context/application/dto/OrderCancellationDtos.java
  - src/main/java/com/example/feat1/DDD/order_context/application/event/OrderCancelledEvent.java
  - src/main/java/com/example/feat1/DDD/order_context/domain/model/KitchenItemStatusView.java
  - src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderDomainException.java
  - src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderStatus.java
  - src/main/java/com/example/feat1/DDD/order_context/domain/port/KitchenItemStatusPort.java
  - src/main/java/com/example/feat1/DDD/order_context/infrastructure/entity/OrderLineEntity.java
  - src/main/java/com/example/feat1/DDD/order_context/infrastructure/presentation/AdminOrderCancellationController.java
  - src/main/java/com/example/feat1/DDD/order_context/infrastructure/presentation/OrderController.java
  - src/main/java/com/example/feat1/DDD/order_context/infrastructure/repository/OrderRepository.java
  - src/main/java/com/example/feat1/DDD/payment_context/application/PaymentAutoRefundService.java
  - src/main/java/com/example/feat1/DDD/payment_context/infrastructure/adapter/OrderCancelledPaymentListener.java
  - src/main/java/com/example/feat1/DDD/payment_context/infrastructure/config/OrderCancelledPaymentKafkaConsumerConfig.java
  - src/main/java/com/example/feat1/DDD/payment_context/infrastructure/entity/PaymentProcessedEventEntity.java
  - src/main/java/com/example/feat1/DDD/payment_context/infrastructure/entity/PaymentRefundEntity.java
  - src/main/java/com/example/feat1/DDD/payment_context/infrastructure/repository/PaymentProcessedEventRepository.java
  - src/main/resources/application.properties
  - src/test/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationReleaseServiceTest.java
  - src/test/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationSettlementServiceTest.java
  - src/test/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketAdvanceServiceTest.java
  - src/test/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketInvalidationServiceTest.java
  - src/test/java/com/example/feat1/DDD/order_context/application/KitchenStatusProjectionServiceTest.java
  - src/test/java/com/example/feat1/DDD/order_context/application/OrderCancellationServiceTest.java
  - src/test/java/com/example/feat1/DDD/order_context/application/OrderConfirmationServiceTest.java
  - src/test/java/com/example/feat1/DDD/order_context/integration/OrderCancellationIntegrationTest.java
  - src/test/java/com/example/feat1/DDD/payment_context/application/PaymentAutoRefundServiceTest.java
findings:
  critical: 3
  warning: 2
  info: 2
  total: 7
status: issues_found
---

# Phase 18: Code Review Report

**Reviewed:** 2026-07-10T05:20:29Z
**Depth:** deep
**Files Reviewed:** 41 (34 main + test companions; count above reflects the deduplicated diff list)
**Status:** issues_found

## Summary

Reviewed the full diff introduced by phase 18 (order/order-item cancellation with cross-context
compensation across order, inventory, kitchen, and payment contexts), cross-referencing call chains
between `OrderCancellationService`, the three new `OrderCancelled` Kafka consumers, and the
pre-existing `KitchenStatusProjectionService`/`InventoryStockService` collaborators they touch
indirectly through shared enums.

The phase's happy-path plumbing (outbox publish, dual-guard idempotency ledgers on
inventory-release, ledger-last-in-tx ordering, DLT wiring, Jackson-3 serdes, pessimistic lock
ordering, IDOR-safe ownership checks) is well executed and matches the documented design. However,
deep tracing of the new `KitchenItemStatus.CANCELLED` and `InventoryMovementType.RESERVATION_RELEASE`
enum constants into code paths *outside* the phase's own new files surfaced a reproducible
NullPointerException in the pre-existing `KitchenStatusProjectionService`, and tracing the
`wholeOrder` flag from `OrderCancellationService` into `PaymentAutoRefundService` surfaced a
financial-correctness defect: the auto-refund gate trusts `wholeOrder=true` as "every line was
actually cancelled," but production code sets that flag unconditionally for the whole-order path
even when the race-safety exclusion (the very mechanism this phase added to close the
cancel-vs-kitchen-advance race) has left one or more lines un-cancelled and still in flight. Neither
gap is exercised by the phase's otherwise-thorough test suite, because every test fixture that
constructs an `OrderCancelledEvent` assumes `wholeOrder == (cancelledLineIds.size() == totalLines)`,
an invariant the production code does not actually enforce.

## Critical Issues

### CR-01: NullPointerException in KitchenStatusProjectionService when a ticket snapshot includes a CANCELLED item

**File:** `src/main/java/com/example/feat1/DDD/order_context/application/KitchenStatusProjectionService.java:67-73, 165-167`

**Issue:** Phase 18 (plan 18-06 / D-7) adds `KitchenItemStatus.CANCELLED` as a new terminal value
consumed by voided ticket items (`KitchenTicketInvalidationService.voidIfStillQueued`). However,
`KitchenStatusProjectionService.ITEM_RANK` — the explicit, ordinal-free rank map this same phase's
own Javadoc says was added specifically so "reordering the enum's declaration can never silently
change derivation" — was **not** updated to include `CANCELLED`:

```java
private static final Map<KitchenItemStatus, Integer> ITEM_RANK =
    Map.of(
        KitchenItemStatus.QUEUED, 0,
        KitchenItemStatus.PREPARING, 1,
        KitchenItemStatus.READY, 2,
        KitchenItemStatus.SERVED, 3,
        KitchenItemStatus.COMPLETED, 4);
...
private int itemRank(KitchenItemStatus status) {
  return ITEM_RANK.get(status);   // ITEM_RANK.get(CANCELLED) == null -> NPE on unboxing
}
```

`deriveTargetStatus` calls `itemRank(...)` inside `allMatch`/`anyMatch` predicates over the **full**
per-ticket item snapshot carried by `KitchenTicketStatusChangedEvent`
(`KitchenTicketAdvanceService.toStatusChangedEvent` maps `ticket.getItems()` — every item on the
ticket, unfiltered, including any that are `CANCELLED` — into that snapshot). This is a completely
mainstream scenario this very phase introduces: a multi-line order has one line partially cancelled
(voided to `CANCELLED` by `KitchenTicketInvalidationService`) while a sibling line on the *same
ticket* continues normally through `QUEUED → PREPARING → READY → SERVED → COMPLETED`. Each of those
sibling advances publishes a fresh `KitchenTicketStatusChangedEvent` whose snapshot still contains
the earlier `CANCELLED` item. As soon as `deriveTargetStatus`'s `allMatch` walk reaches that item
(guaranteed once any other item's rank check evaluates `true` rather than short-circuiting `false`,
e.g. once the sibling reaches `SERVED`), `itemRank(CANCELLED)` returns `null` and unboxing it to the
primitive `int` comparison throws `NullPointerException`.

This crashes `KitchenStatusProjectionService.onTicketStatusChanged` (a `@Transactional` Kafka
consumer method) for any order that combines a cancelled line with a sibling line that keeps
progressing — exactly the use case phase 18's own partial-cancel feature (D-4) exists to support.
The transaction rolls back, the container retries per its `DefaultErrorHandler` backoff, and the
event ultimately lands on the DLT even though nothing is actually wrong with the payload — this
silently stalls the order's aggregate-status projection for the *entire* order (not just the
cancelled line) going forward, since the consumer never advances past this poison record.

No test in `KitchenStatusProjectionServiceTest` (nor anywhere else in the diff) constructs an
`ItemStatus` snapshot containing `KitchenItemStatus.CANCELLED`, so this gap is untested.

**Fix:**
```java
private static final Map<KitchenItemStatus, Integer> ITEM_RANK =
    Map.of(
        KitchenItemStatus.QUEUED, 0,
        KitchenItemStatus.PREPARING, 1,
        KitchenItemStatus.READY, 2,
        KitchenItemStatus.SERVED, 3,
        KitchenItemStatus.COMPLETED, 4);
        // CANCELLED intentionally excluded from rank; filter it out of the snapshot before ranking.

private OrderStatus deriveTargetStatus(List<ItemStatus> items) {
  List<ItemStatus> active =
      items.stream().filter(i -> i.status() != KitchenItemStatus.CANCELLED).toList();
  if (active.isEmpty()) {
    return null; // every item on the ticket was voided -- nothing to derive a fulfillment status from
  }
  // ...existing allMatch/anyMatch logic, but over `active` instead of `items`
}
```
Add a regression test that mixes a `CANCELLED` item with `SERVED`/`COMPLETED` siblings and asserts
no exception is thrown and the correct status is still derived from the active items.

---

### CR-02: Whole-order cancel forces terminal CANCELLED + wholeOrder=true even when a line was excluded as already-preparing, triggering an incorrect full refund

**File:** `src/main/java/com/example/feat1/DDD/order_context/application/OrderCancellationService.java:111-129`

**Issue:** `applyCancellation` computes `cancelledLineIds` by excluding any line whose kitchen item is
already `AT_OR_AFTER_PREPARING` (the synchronous `KitchenItemStatusPort` read this phase added
specifically to close the cancel-vs-kitchen-advance race, T-18-02-03). That exclusion is correctly
applied per-line. But the **order-level** outcome for the whole-order path ignores it entirely:

```java
if (wholeOrder) {
  order.setStatus(OrderStatus.CANCELLED);
}
...
OrderCancelledEvent event =
    new OrderCancelledEvent(
        UUID.randomUUID(), OrderCancelledEvent.TYPE, now, order.getId(),
        wholeOrder,                 // <-- always the caller's boolean, never reconciled
        cancelledLineIds,           // <-- may be a STRICT SUBSET of order.getLines()
        order.getLines().size());
```

`wholeOrder` here is simply "which endpoint/method was called," not "were all lines actually
cancelled." Whenever the pessimistic-lock-protected `kitchenItemStatusPort.findStatuses` read finds
that one or more lines have already raced ahead to `PREPARING` (a realistic, expected outcome of the
very TOCTOU race this phase's own threat model calls out as only "mitigated," not eliminated — see
18-02-PLAN.md's T-18-02-03 residual-window note), `cancelOrder(...)`:

1. Still unconditionally sets `order.setStatus(CANCELLED)` — a **terminal** state per
   `KitchenStatusProjectionService`'s `REJECTED`/`CANCELLED` guard — even though the excluded line is
   still actively being prepared/served for this same order. The order's aggregate status can never
   again reflect that ongoing fulfillment.
2. Still publishes `OrderCancelledEvent(wholeOrder=true, ...)`. `PaymentAutoRefundService` treats
   `wholeOrder=true` as authorization to refund the **entire unrefunded remainder of every payment on
   the order** (D-6's "Auto-refund applies to whole-order cancel ONLY" is keyed off this exact flag).
   The customer is refunded in full for food that is still being actively prepared and will still be
   served.
3. In the degenerate case where **every** requested line is already preparing, `cancelledLineIds` is
   empty, `order.getLines().size()` (totalLines) is unchanged, and the order/refund/terminal-status
   consequences above still fire even though **zero** lines were actually released — i.e. a
   whole-order "cancel" that cancels nothing still fully refunds the order and permanently marks it
   `CANCELLED`.

This is not merely a hypothetical: `InventoryReservationReleaseServiceTest`,
`PaymentAutoRefundServiceTest`, and `OrderCancellationServiceTest` all construct
`OrderCancelledEvent` fixtures where `wholeOrder` is derived as
`cancelledLineIds.size() == totalLines` (see e.g.
`InventoryReservationReleaseServiceTest.event(...)`), which is exactly the invariant the production
code fails to enforce — the test suite's own fixtures assume a contract the service under test does
not honor, which is why this gap slipped through 252/252 green and the phase verification's 27/27
"observable truths."

**Fix:** Reconcile `wholeOrder` with what actually happened before constructing the event, and avoid
flipping the order to a terminal state when the cancellation was not, in fact, complete:
```java
boolean allLinesCancelled = cancelledLineIds.size() == candidateLines.size()
    && candidateLines.size() == order.getLines().size();
boolean effectiveWholeOrder = wholeOrder && allLinesCancelled;

if (effectiveWholeOrder) {
  order.setStatus(OrderStatus.CANCELLED);
} else if (wholeOrder && cancelledLineIds.isEmpty()) {
  // nothing was actually cancellable -- surface this the same way partial-cancel does today
  throw OrderDomainException.noCancellableLines();
}
...
OrderCancelledEvent event = new OrderCancelledEvent(
    ..., effectiveWholeOrder, cancelledLineIds, order.getLines().size());
```
Add a regression test in `OrderCancellationServiceTest` for whole-order cancel where one line is
already `AT_OR_AFTER_PREPARING`: assert the order status is NOT forced to `CANCELLED` (or, if product
intent is truly "reject the whole-order cancel outright" per the plan's other allowed option, assert
`cancelWindowClosed()`/an equivalent rejection instead) and that the published event's `wholeOrder`
is `false` so `PaymentAutoRefundService` does not over-refund.

---

### CR-03: InventoryMovementType.RESERVATION_RELEASE not classified by isInbound()/isOutbound()/isCount() — manual movement endpoint silently corrupts stock-on-hand

**File:** `src/main/java/com/example/feat1/DDD/inventory_context/domain/model/InventoryMovementType.java:22-41`

**Issue:** This phase adds the `RESERVATION_RELEASE` enum constant but does not add it to any of the
three classification predicates:
```java
public boolean isInbound() {
  return this == RECEIPT || this == ADJUSTMENT_IN;
}
public boolean isOutbound() {
  return this == ADJUSTMENT_OUT || this == WASTE || this == CONSUMPTION;  // RESERVATION_RELEASE missing
}
public boolean isCount() {
  return this == STOCK_COUNT;
}
```
The pre-existing, unmodified `InventoryStockService.recordMovement` (bound to the staff/ADMIN
`POST /admin/inventory/movements` endpoint, `InventoryMovementType type = request.type()` taken
directly from the JSON request body with no server-side allow-list) branches on exactly these three
predicates:
```java
if (type.isInbound()) { ... }
else if (type.isOutbound()) { ... }
else { // STOCK_COUNT: explicit correction path that sets on-hand to the counted quantity.
  resulting = baseQuantity;
  delta = resulting.subtract(current);
}
```
Because `RESERVATION_RELEASE` is neither inbound, outbound, nor "count," it falls into the `else`
branch and is silently treated as an **absolute stock-count set**: whatever quantity a staff member
submits with `type: "RESERVATION_RELEASE"` overwrites `quantityOnHand` outright, and a movement row is
persisted claiming `movementType = RESERVATION_RELEASE` even though the mutation it actually performed
bears no resemblance to a reservation release (which is defined everywhere else in this phase as
"decrement `reservedQuantity` only, never touch `quantityOnHand`," per
`InventoryReservationReleaseService`'s own Javadoc and `InventoryMovementType.RESERVATION_RELEASE`'s
own Javadoc). This silently corrupts stock-on-hand data and produces an audit trail that
misrepresents what happened, instead of rejecting the (system-only) movement type outright.

**Fix:** Either explicitly reject system-only movement types from the manual endpoint, or extend the
classification (and treat `RESERVATION_RELEASE` as a special no-quantity-on-hand-effect case, not the
STOCK_COUNT fallthrough):
```java
public boolean isSystemOnly() {
  return this == CONSUMPTION || this == RESERVATION_RELEASE;
}
```
and in `InventoryStockService.recordMovement`:
```java
if (type.isSystemOnly()) {
  throw InventoryDomainException.movementInvalid(
      type + " movements are system-generated and cannot be recorded manually");
}
```
(`CONSUMPTION` predates this phase and was already reachable via this same gap — pre-existing, out of
this phase's diff — but `RESERVATION_RELEASE` is newly introduced by phase 18 and should not ship
without being reconciled against this pre-existing dispatch.)

## Warnings

### WR-01: InventoryReservationReleaseService does not null-guard event.cancelledLineIds() before use

**File:** `src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationReleaseService.java:90-98`

**Issue:**
```java
List<UUID> cancelledLineIds = event.cancelledLineIds();

boolean allLinesAlreadyReleased =
    !cancelledLineIds.isEmpty()
        && cancelledLineIds.stream()
            .allMatch(lineId -> lineReleaseRepository.existsByOrderIdAndOrderLineId(orderId, lineId));
```
`OrderCancelledEvent.cancelledLineIds()` is a `List<UUID>` (nullable by the record's own type, and by
the Jackson-3 deserializer's default handling of an absent/null JSON field on a poison or
schema-drifted payload). If it is ever `null`, `cancelledLineIds.isEmpty()` throws
`NullPointerException` before any of the method's actual idempotency/locking logic runs.
`KitchenTicketInvalidationService.onOrderCancelled` (consuming the exact same event type, in the same
phase) explicitly guards this same field (`if (event.cancelledLineIds() != null) { ... }`), so this is
an inconsistency in defensive posture across the phase's three new consumers, not merely a
theoretical concern.

**Fix:**
```java
List<UUID> cancelledLineIds =
    event.cancelledLineIds() == null ? List.of() : event.cancelledLineIds();
```

### WR-02: Test fixtures across three services assume a wholeOrder invariant production code does not enforce

**File:** `src/test/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationReleaseServiceTest.java:334-343`,
`src/test/java/com/example/feat1/DDD/payment_context/application/PaymentAutoRefundServiceTest.java:50-53`,
`src/test/java/com/example/feat1/DDD/order_context/application/OrderCancellationServiceTest.java`

**Issue:** Every test-side `OrderCancelledEvent` factory in the phase (e.g.
`InventoryReservationReleaseServiceTest.event(orderId, cancelledLineIds, totalLines)` derives
`wholeOrder` as `cancelledLineIds.size() == totalLines`) encodes the intended invariant "wholeOrder is
true only when every line was actually cancelled." `OrderCancellationServiceTest` never exercises a
whole-order cancel where a line is excluded via the kitchen port (only the *partial*-cancel exclusion
test, `partialCancelExcludesLineAlreadyAtOrAfterPreparingViaKitchenPort`, covers exclusion at all).
Because the production `OrderCancellationService` doesn't actually derive `wholeOrder` this way (see
CR-02), none of the three consumer test suites — despite being individually thorough — can catch the
mismatch, since they only ever receive hand-constructed events that already satisfy the invariant the
production code violates.

**Fix:** Once CR-02 is fixed, add the corresponding integration-level assertion (e.g. in
`OrderCancellationServiceTest`) that a whole-order cancel with a mixed cancellable/already-preparing
line set publishes `wholeOrder=false` (or is rejected outright, per whichever fix direction is taken),
closing the gap between the unit fixtures' assumed contract and the real one.

## Info

### IN-01: OrderDomainException.lineNotCancellable() / LINE_NOT_CANCELLABLE is dead code

**File:** `src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderDomainException.java:15,63-66`

**Issue:** `LINE_NOT_CANCELLABLE` and the `lineNotCancellable()` factory are defined but never
referenced anywhere in the codebase (confirmed via full-repo grep). `OrderCancellationService`
uses `noCancellableLines()` for the equivalent all-requested-lines-uncancellable case instead,
leaving this exception code entirely unreachable.

**Fix:** Remove the unused constant/factory, or wire it into a call site it was presumably intended
for (e.g. a future single-line-specific 404/409 distinct from "no lines in the request were
cancellable").

### IN-02: KitchenTicketInvalidationService's idempotency mechanism differs from Inventory's documented "dual guard" pattern without explanation

**File:** `src/main/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketInvalidationService.java:18-32`

**Issue:** `InventoryReservationReleaseService`'s Javadoc explicitly documents and relies on two
independent idempotency guards (the eventId ledger and a per-line "already released" durable row).
`KitchenTicketInvalidationService` relies on a single eventId-ledger guard plus an implicit
state-based guard (`item.getStatus() != QUEUED`) that is not framed anywhere in its Javadoc as the
second half of a deliberate dual-guard design (unlike Inventory's explicit "Neither is redundant"
framing). Functionally this is safe — the state check does prevent double-voiding — but the asymmetry
in how the two nearly-identical consumers document/justify their idempotency strategy is worth
calling out for consistency with the phase's own stated convention.

**Fix (recommend, non-blocking):** Add a short Javadoc note to `KitchenTicketInvalidationService`
explaining that the per-item `status != QUEUED` check is the state-based equivalent of Inventory's
per-line durable guard, so the two consumers' documented idempotency strategies read as intentionally
consistent rather than divergent.

---

_Reviewed: 2026-07-10T05:20:29Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
