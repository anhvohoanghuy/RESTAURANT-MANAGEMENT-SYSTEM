---
phase: 18-order-and-order-item-cancellation-with-compensation-release-
verified: 2026-07-10T12:15:00Z
status: passed
score: 27/27 must-haves verified
has_blocking_gaps: false
overrides_applied: 0
---

# Phase 18: Order and order-item cancellation with compensation Verification Report

**Phase Goal:** Add a cancellation capability for both a whole order and individual order items, with cross-context compensation. Cancel allowed ONLY before kitchen starts (SUBMITTED/PENDING_CONFIRMATION/CONFIRMED, never PREPARING+); partial cancel limited to non-PREPARING items. Customer (own order, early states, ownership-checked) and staff/ADMIN (any order in window) can cancel. Adds terminal CANCELLED order status + per-item cancel; releases held Inventory reservation (reserved → available) for cancelled scope; recomputes order total on partial cancel; and for a paid order automatically triggers a Payment refund for the amount already paid via the existing transactional-outbox / idempotent-consumer event pattern (no synchronous cross-context call). Maven suite stays green; no new dependencies.

**Verified:** 2026-07-10T12:15:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | OrderStatus has a terminal CANCELLED value appended after REJECTED | ✓ VERIFIED | `OrderStatus.java` — `CANCELLED` is the last enum constant, after `REJECTED`; pinned CONFIRMED..COMPLETED ordering comment unchanged |
| 2 | KitchenStatusProjectionService never overwrites a CANCELLED order with a fulfillment status | ✓ VERIFIED | `KitchenStatusProjectionService.java:96` — `if (order.getStatus() == OrderStatus.REJECTED \|\| order.getStatus() == OrderStatus.CANCELLED) return;`; `CANCELLED` absent from `FULFILLMENT_RANK` |
| 3 | A stale OrderStockResult arriving after CANCELLED does not resurrect the order | ✓ VERIFIED | `OrderConfirmationServiceTest` regression test `cancelledOrderIsNotResurrectedByStaleStockResult`, part of 252-test green suite |
| 4 | OrderCancelledEvent contract exists as an outbox payload consumable cross-context | ✓ VERIFIED | `OrderCancelledEvent.java` — record with `eventId, eventType, occurredAt, orderId, wholeOrder, cancelledLineIds, totalLines`; `TYPE="OrderCancelled"`; `order.events.order-cancelled-topic` property present in `application.properties` |
| 5 | Cancel is rejected when order status is not SUBMITTED/PENDING_CONFIRMATION/CONFIRMED | ✓ VERIFIED | `OrderCancellationService.java:45-46,84-86` — `CANCELLABLE_STATUSES` EnumSet guard throws `cancelWindowClosed()`; integration test `cancellingOrderPastCancelWindowIsRejected` passes |
| 6 | A customer cancelling another user's order gets orderNotFound (404, IDOR-safe) | ✓ VERIFIED | `OrderCancellationService.resolveAndLock` — `findByIdAndUserId` pre-check before lock; integration test `customerCancellingAnotherUsersOrderIsIdorSafe404` passes (5/5 in `OrderCancellationIntegrationTest`) |
| 7 | Whole-order cancel sets CANCELLED and publishes OrderCancelledEvent in the same transaction | ✓ VERIFIED | `applyCancellation` — single `@Transactional` method sets `order.setStatus(CANCELLED)` and calls `outboxWriter.save(...)` in the same method; integration test asserts outbox row persisted |
| 8 | Partial cancel marks non-PREPARING lines cancelledAt and recomputes order.total from remaining lines | ✓ VERIFIED | `recomputeTotal` sums `lineTotal` over lines where `cancelledAt == null`, called on every successful cancel; `OrderCancellationServiceTest` (7/7 pass) |
| 9 | A line whose kitchen item is already >= PREPARING is excluded/rejected via a synchronous kitchen read | ✓ VERIFIED | `kitchenItemStatusPort.findStatuses(orderId)` called inside the locked transaction; `beforePreparing` check excludes AT_OR_AFTER_PREPARING lines |
| 10 | On OrderCancelled, the held reservation for each cancelled line is released (reservedQuantity decremented, quantityOnHand untouched) | ✓ VERIFIED | `InventoryReservationReleaseService.java` — decrements `reservedQuantity` only; `setQuantityOnHand` never called (grep confirms only a comment mentions it) |
| 11 | Release clamps reservedQuantity at zero and never goes negative | ✓ VERIFIED | Lines 158-165 — `newReserved.compareTo(BigDecimal.ZERO) < 0` clamps to `ZERO` with a warn log |
| 12 | Release is idempotent on Kafka redelivery (per-eventId ledger + per-(orderId,orderLineId) guard) | ✓ VERIFIED | Dual guard: `processedEventRepository.existsByEventIdAndConsumerName` + `lineReleaseRepository.existsByOrderIdAndOrderLineId`; `InventoryReservationReleaseServiceTest` (11/11 pass) |
| 13 | A RESERVATION_RELEASE audit movement is written with a null (system) actor | ✓ VERIFIED | Line 179/187 — `movement.setMovementType(InventoryMovementType.RESERVATION_RELEASE); movement.setActorId(null);` |
| 14 | settledCount + releasedCount >= totalLines flips the reservation to a terminal state correctly | ✓ VERIFIED | `InventoryReservationSettlementService.java:210-212` and `InventoryReservationReleaseService.java:204-207` both use the widened `settledCount + releasedCount >= totalLines` guard |
| 15 | On a whole-order OrderCancelled, every payment of the order is refunded for its unrefunded remainder | ✓ VERIFIED | `PaymentAutoRefundService.onOrderCancelled` iterates `findByOrderIdOrderByCreatedAtAsc`, computes `remaining = amount - refunded`, calls `recordRefund` when `remaining > 0` |
| 16 | A partial-cancel event (wholeOrder=false) triggers NO refund (D-6) | ✓ VERIFIED | Line 48-50 — hard `if (!event.wholeOrder()) return;` gate before any refund; `PaymentAutoRefundServiceTest` (5/5 pass) covers this case |
| 17 | Auto-refund is idempotent on Kafka redelivery via the new payment_processed_events ledger | ✓ VERIFIED | `PaymentProcessedEventEntity` (table `payment_processed_events`, unique `(event_id, consumer_name)`); ledger pre-check + ledger-row-saved-last idiom |
| 18 | System-triggered refunds record a null actor (no fabricated user) | ✓ VERIFIED | `recordRefund(null, payment.getId(), ...)`; `PaymentRefundEntity.actorUserId` made nullable (`@Column(name = "actor_user_id")`, no `nullable=false`) |
| 19 | A customer can cancel their own order via POST /orders/{orderId}/cancel | ✓ VERIFIED | `OrderController.java` — `@PostMapping("/{orderId}/cancel")`; integration test `customerCancelsOwnOrderAndLeavesCancelledOutboxRow` passes |
| 20 | A customer can partial-cancel via POST /orders/{orderId}/items/{lineId}/cancel | ✓ VERIFIED | `OrderController.java` — `@PostMapping("/{orderId}/items/{lineId}/cancel")`, wraps path lineId into `CancelOrderLinesRequest` |
| 21 | Staff/ADMIN can cancel any order via /admin/orders/{orderId}/cancel (+ item variant) | ✓ VERIFIED | `AdminOrderCancellationController.java` — both endpoints present, `userId=null` (no ownership check); integration test `staffCancelsAnyOrderViaAdminRoute` passes |
| 22 | A customer cancelling another user's order receives 404 (IDOR-safe) | ✓ VERIFIED | Same as truth #6, confirmed end-to-end at HTTP layer |
| 23 | A non-staff user calling the /admin cancel routes is rejected by Spring Security | ✓ VERIFIED | `SecurityConfig.java:61-62` — `/admin/orders/**` mapped `hasAnyRole("ADMIN","STAFF")` (pre-existing, unmodified); integration test `nonStaffUserIsRejectedFromAdminCancelRoute` passes (403/401) |
| 24 | On OrderCancelled, each cancelled line's still-QUEUED kitchen item is voided so it cannot be advanced | ✓ VERIFIED | `KitchenTicketInvalidationService.voidIfStillQueued` — sets `KitchenItemStatus.CANCELLED` only when current status is `QUEUED` |
| 25 | A kitchen item already >= PREPARING is NEVER touched by invalidation (defense-in-depth) | ✓ VERIFIED | Guard `if (item.getStatus() != KitchenItemStatus.QUEUED) return;` before any mutation; `KitchenTicketInvalidationServiceTest` (8/8 pass) covers this |
| 26 | A voided/CANCELLED kitchen item cannot be advanced to PREPARING (no rogue settle-trigger) | ✓ VERIFIED | `KitchenTicketAdvanceService.java:87` — `case CANCELLED -> false;` in the exhaustive transition switch; regression test `advancingFromVoidedCancelledIsRejected` in `KitchenTicketAdvanceServiceTest` (10/10 pass) |
| 27 | Invalidation is idempotent on Kafka redelivery via the kitchen processed-events ledger | ✓ VERIFIED | Ledger pre-check (`existsByEventIdAndConsumerName`) + ledger-row-saved-last idiom in `KitchenTicketInvalidationService.onOrderCancelled` |

**Score:** 27/27 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `order_context/domain/model/OrderStatus.java` | CANCELLED terminal status | ✓ VERIFIED | Appended last, load-bearing comment intact |
| `order_context/application/event/OrderCancelledEvent.java` | Outbox payload record | ✓ VERIFIED | Exact field set matches plan spec |
| `order_context/application/OrderCancellationService.java` | Whole-order + partial cancel logic | ✓ VERIFIED | 161 lines (exceeds min_lines: 90); lock, ownership, window guard, kitchen read, recompute, outbox publish all present |
| `order_context/domain/port/KitchenItemStatusPort.java` | Cross-context kitchen status read | ✓ VERIFIED | `interface KitchenItemStatusPort { Map<UUID, KitchenItemStatusView> findStatuses(UUID) }` |
| `kitchen_context/infrastructure/adapter/KitchenItemStatusAdapter.java` | kitchen_context port implementation | ✓ VERIFIED | `@Component implements KitchenItemStatusPort`, `@Transactional(readOnly=true)` |
| `inventory_context/application/InventoryReservationReleaseService.java` | Inverse of settlement service | ✓ VERIFIED | 239 lines (exceeds min_lines: 90); reserved-only decrement, audit movement, idempotent |
| `inventory_context/infrastructure/entity/InventoryLineReleaseEntity.java` | Per-line release ledger | ✓ VERIFIED | Table `inventory_line_releases`, unique `(order_id, order_line_id)` |
| `payment_context/infrastructure/entity/PaymentProcessedEventEntity.java` | Payment's first idempotency ledger | ✓ VERIFIED | Table `payment_processed_events`, unique `(event_id, consumer_name)` |
| `payment_context/application/PaymentAutoRefundService.java` | Whole-order-gated auto-refund | ✓ VERIFIED | 89 lines (exceeds min_lines: 60); reuses `PaymentService.recordRefund` |
| `order_context/infrastructure/presentation/AdminOrderCancellationController.java` | Staff/ADMIN cancel endpoints | ✓ VERIFIED | `/admin/orders/{orderId}/cancel` + item variant, no class-level security annotation (relies on matcher) |
| `order_context/integration/OrderCancellationIntegrationTest.java` | End-to-end authorization coverage | ✓ VERIFIED | 264 lines (exceeds min_lines: 60); 5/5 tests pass |
| `kitchen_context/application/KitchenTicketInvalidationService.java` | Guarded idempotent void of QUEUED items | ✓ VERIFIED | 109 lines (exceeds min_lines: 50); lock-then-guard, ledger-last-in-tx |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `KitchenStatusProjectionService.onTicketStatusChanged` | `OrderStatus.CANCELLED` | terminal-status early return guard | ✓ WIRED | Line 96 guard confirmed |
| `OrderCancellationService` | `OutboxWriter.save` | same-transaction outbox publish | ✓ WIRED | `outboxWriter.save("ORDER", ..., OrderCancelledEvent.TYPE, orderCancelledTopic, ..., event)` inside `@Transactional` method |
| `OrderCancellationService` | `KitchenItemStatusPort.findStatuses` | synchronous cross-context PREPARING guard | ✓ WIRED | Called inside locked transaction before line exclusion decision |
| `OrderCancelledInventoryListener` | `InventoryReservationReleaseService.onOrderCancelled` | `@KafkaListener` thin delegate on `orders.cancelled` | ✓ WIRED | Confirmed topic/groupId/containerFactory + delegate call |
| `InventoryReservationReleaseService` | `RecipeRequirementResolver + OrderLineLookupPort` | re-resolve per-line requirements | ✓ WIRED | `resolveLineRequirements` calls `recipeRequirementResolver.resolveForTarget`/`accumulate` via `orderLineLookupPort.findLine` |
| `OrderCancelledPaymentListener` | `PaymentAutoRefundService.onOrderCancelled` | `@KafkaListener` thin delegate | ✓ WIRED | Confirmed |
| `PaymentAutoRefundService` | `PaymentService.recordRefund` | per-payment refund, null system actor | ✓ WIRED | `paymentService.recordRefund(null, payment.getId(), new RecordRefundRequest(...))` |
| `OrderController` | `OrderCancellationService` | customer cancel endpoints resolving `principal.getId()` | ✓ WIRED | `orderCancellationService.cancelOrder(principal.getId(), orderId)` |
| `AdminOrderCancellationController` | `OrderCancellationService` | staff/ADMIN cancel endpoints | ✓ WIRED | `orderCancellationService.cancelOrder(null, orderId)` |
| `OrderCancelledKitchenListener` | `KitchenTicketInvalidationService.onOrderCancelled` | `@KafkaListener` thin delegate | ✓ WIRED | Confirmed |
| `KitchenTicketAdvanceService.advance` | `KitchenItemStatus.CANCELLED` | forward-only guard refuses to advance a voided item | ✓ WIRED | `case CANCELLED -> false;` in exhaustive switch |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full authorization matrix (own-order success, IDOR 404, staff any-order, non-staff 403/401, window-guard rejection) | `./mvnw -q -Dtest=OrderCancellationIntegrationTest test` | Tests run: 5, Failures: 0, Errors: 0 | ✓ PASS |
| OrderCancellationService unit behavior (window/ownership/whole/partial/exclusion) | `./mvnw -q -Dtest=OrderCancellationServiceTest test` | Tests run: 7, Failures: 0, Errors: 0 | ✓ PASS |
| Inventory release idempotency/clamp/audit | `./mvnw -q -Dtest=InventoryReservationReleaseServiceTest test` | Tests run: 11, Failures: 0, Errors: 0 | ✓ PASS |
| Payment auto-refund gating/idempotency | `./mvnw -q -Dtest=PaymentAutoRefundServiceTest test` | Tests run: 5, Failures: 0, Errors: 0 | ✓ PASS |
| Kitchen invalidation guard/idempotency | `./mvnw -q -Dtest=KitchenTicketInvalidationServiceTest test` | Tests run: 8, Failures: 0, Errors: 0 | ✓ PASS |
| Kitchen advance-guard regression (CANCELLED not advanceable) | `./mvnw -q -Dtest=KitchenTicketAdvanceServiceTest test` | Tests run: 10, Failures: 0, Errors: 0 | ✓ PASS |
| Full workspace suite (independently re-summed from surefire reports) | `grep -h "Tests run" target/surefire-reports/*.txt \| awk ...` | total run: 252, failures: 0, errors: 0 (matches the reported 252/0/0 BUILD SUCCESS) | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| CANCEL-01 | 18-02 | Cancel window guard (SUBMITTED/PENDING_CONFIRMATION/CONFIRMED only) | ✓ SATISFIED | `CANCELLABLE_STATUSES` EnumSet guard |
| CANCEL-02 | 18-02, 18-05 | Customer-own + staff/ADMIN authorization | ✓ SATISFIED | IDOR-safe `findByIdAndUserId` + `/admin/orders/**` RBAC, proven end-to-end |
| CANCEL-03 | 18-05 | Whole-order cancel endpoint | ✓ SATISFIED | `POST /orders/{orderId}/cancel` + `POST /admin/orders/{orderId}/cancel` |
| CANCEL-04 | 18-02, 18-05 | Partial item-cancel endpoint (non-PREPARING items only) with total recompute | ✓ SATISFIED | `cancelOrderLines` + `POST /orders/{orderId}/items/{lineId}/cancel` |
| CANCEL-05 | 18-03 | Inventory reservation release on cancel (idempotent) | ✓ SATISFIED | `InventoryReservationReleaseService`, dual idempotency guard |
| CANCEL-06 | 18-04 | Automatic Payment refund on cancel of a paid order (event-driven) | ✓ SATISFIED | `PaymentAutoRefundService`, whole-order gated (D-6), Payment's first Kafka consumer |
| CANCEL-07 | 18-01 | CANCELLED terminal status + state-machine/idempotency guards | ✓ SATISFIED | `OrderStatus.CANCELLED` + dual terminal guards |
| CANCEL-08 | 18-06 | Kitchen ticket void on cancel (D-7 defensive backstop) | ✓ SATISFIED | `KitchenTicketInvalidationService` + advance-guard `case CANCELLED -> false;` |

**Note:** CANCEL-01 through CANCEL-08 are ROADMAP-declared requirement labels specific to Phase 18 (the project-level `.planning/REQUIREMENTS.md` predates this phase and does not separately enumerate them — ROADMAP.md's phase-18 "Requirements" prose is the authoritative source, cross-checked above). No orphaned requirements found: every CANCEL-XX ID referenced in a PLAN's `requirements:` frontmatter is claimed by exactly one plan's `requirements-completed`, and all 8 map to verified code.

### Anti-Patterns Found

None. Scanned all 32 files created/modified across the phase's 6 plans for `TBD|FIXME|XXX|TODO|HACK|PLACEHOLDER`, "not yet implemented"/"coming soon", stray `.remove(` calls on order lines, and stray `setQuantityOnHand` calls in the release service — all clean. No legacy Jackson-2 serializers found in any of the three new Kafka consumer configs (Inventory, Payment, Kitchen) — all three use `JacksonJsonDeserializer`/`JacksonJsonSerializer` (Jackson-3) exclusively.

### Human Verification Required

None. All must-haves are verified by automated unit tests, an end-to-end MockMvc/Spring-Security integration test, and direct source inspection. No visual, real-time, or external-service-dependent behavior in this phase's scope.

### Gaps Summary

No gaps. All 27 observable truths across the phase's 6 plans are verified against actual, compiling, tested code — not just SUMMARY.md claims. Every artifact meets or exceeds its `min_lines` threshold and contains its required pattern. Every key link is wired (grep-confirmed call sites, not just declared interfaces). The full Maven suite is green (252/252, independently re-summed from `target/surefire-reports/*.txt`, matching the reported BUILD SUCCESS). No new dependencies were introduced. The `/admin/orders/**` Spring Security matcher was confirmed pre-existing and unmodified, and the integration test proves the full authorization matrix (owner success, IDOR 404, staff any-order, non-staff 403/401, window-guard rejection) through real Spring Security rather than mocked authorization.

---

_Verified: 2026-07-10T12:15:00Z_
_Verifier: Claude (gsd-verifier)_
