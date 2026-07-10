# Phase 18: Order & order-item cancellation with compensation — Context

**Gathered:** 2026-07-10
**Status:** Ready for planning
**Source:** Interactive discussion (4 gray areas locked with the user on 2026-07-10)

<domain>
## Task Boundary

Add a cancellation capability for (a) a whole order and (b) individual order items,
with cross-context compensation. Spans Order (owns cancel + status), Inventory
(release held reservation), and Payment (auto-refund). Follows the project's existing
transactional-outbox + idempotent-consumer messaging patterns; no synchronous
cross-context calls; no new dependencies; Maven suite stays green.
</domain>

<decisions>
## Implementation Decisions (LOCKED — do not revisit)

### D-1 Cancel window
- An order/item may be cancelled ONLY **before the kitchen starts**: order status
  `SUBMITTED`, `PENDING_CONFIRMATION`, or `CONFIRMED`.
- Once an order/item is `PREPARING` or later (`READY`/`SERVED`/`COMPLETED`), cancel is
  rejected. This keeps compensation simple: nothing has been consumed yet, so only the
  held reservation must be released (no waste accounting, no settled-stock reversal).

### D-2 Who can cancel
- **Both**:
  - A **customer** may cancel **their OWN** order — early states only — with an
    ownership check (mirror existing order-ownership checks in order_context).
  - **Staff / ADMIN** may cancel **any** order within the cancel window (mirror the
    existing `/admin/**` ADMIN/STAFF authorization).

### D-3 Refund on a paid order
- **Automatic**: cancelling a paid order automatically triggers a **Payment refund** for
  the amount already paid.
- Delivered event-driven (outbox → Payment consumer), NOT a synchronous call, mirroring
  the Phase 15/16/17 saga pattern. Idempotent (processed-events ledger).

### D-4 Partial cancel (cancel a few items)
- Only items **not yet PREPARING** can be cancelled.
- Cancelling item(s) **releases their held Inventory reservation** (`reserved →
  available`) for exactly those lines and **recomputes the order total**.
- Items already PREPARING/settled are NOT cancellable in this phase (blocked, not
  force-cancelled).

### D-5 Refund delivery mechanism
- **Event-driven** (locked, confirmed after research surfaced that Payment has no
  consumer infra today). Order publishes a cancel/refund-requested event via the
  transactional outbox; **Payment gains its FIRST Kafka consumer + a processed-events
  idempotency ledger** (built from scratch this phase, mirroring the order/inventory
  ledger + DLT convention) to apply the refund. No synchronous cross-context call.
- Note: this is a larger lift than a simple trigger — building Payment's first consumer
  and ledger is explicitly in scope.

### D-6 Partial-cancel refund scope
- Auto-refund applies to **whole-order cancel ONLY**. Partial item-cancel of a paid
  order does **NOT** auto-refund the delta — it only releases the cancelled items'
  reservation and recomputes the order total; any owed refund is left for staff to
  handle manually (avoids delta-refund complexity; orders before PREPARING are usually
  unpaid anyway).

### D-7 Kitchen ticket invalidation on cancel
- **Yes** — Kitchen Context consumes the cancel event and **voids/cancels the
  corresponding ticket item(s)**, so staff cannot later advance an already-cancelled
  line (which would otherwise fire a settlement that deducts stock already released).
  This closes the cancel-vs-kitchen-advance race. Idempotent consumer, same ledger/DLT
  pattern. (Cancel window is still bounded to pre-PREPARING per D-1; this consumer is
  the defensive backstop for the create→confirm→ticket race.)

### Claude's Discretion
- Exact new endpoint paths/verbs, DTO shapes, and whether whole-order cancel is
  modelled as "cancel all remaining cancellable items" vs a distinct order-level
  transition — planner decides, consistent with existing order_context conventions.
- Event names/payloads for the cancel → inventory-release and cancel → payment-refund
  messages — follow existing `OrderCreated`/settle-trigger naming conventions.
- Whether per-item cancel introduces an item-level `CANCELLED` state or a soft
  `cancelledAt` marker on the line — planner decides based on current OrderEntity/line model.
</decisions>

<specifics>
## Specific Ideas / Constraints

- New terminal order status `CANCELLED` on `OrderStatus` (currently: SUBMITTED,
  PENDING_CONFIRMATION, CONFIRMED, PREPARING, READY, SERVED, COMPLETED, REJECTED — no
  CANCELLED today). Respect the load-bearing forward-only fulfillment-rank ordering
  comment in OrderStatus.java; CANCELLED must be handled as terminal like REJECTED
  (see KitchenStatusProjectionService REJECTED-is-terminal guard as the analog).
- Inventory reservation release is the inverse of the Phase 16 settlement path
  (`reserved → available`, non-negative clamp, idempotent, audit movement). Reuse the
  shared recipe/requirement resolution where applicable.
- Payment Context already has a manual refund capability (Phase 11) — reuse it; this
  phase only adds the automatic trigger.
- All new consumers idempotent via the established processed-events ledger + DLT.
</specifics>

<canonical_refs>
## Canonical References

- `OrderStatus.java` (order_context/domain/model) — status enum + forward-only ordering.
- `OrderConfirmationService.java` — REJECTED terminal handling analog for CANCELLED.
- `KitchenStatusProjectionService.java` — REJECTED-is-terminal guard pattern.
- Phase 16 `InventoryReservationSettlementService` — settlement path to invert for release.
- Phase 11 Payment refund — manual refund to trigger automatically.
- Transactional outbox: `shared/outbox/**` — event delivery pattern to follow.
</canonical_refs>
