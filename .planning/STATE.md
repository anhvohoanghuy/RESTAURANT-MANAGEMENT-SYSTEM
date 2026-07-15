---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Admin UI
current_phase: 19
status: ready_to_plan
last_updated: 2026-07-15T04:14:55.294Z
last_activity: 2026-07-15
progress:
  total_phases: 9
  completed_phases: 7
  total_plans: 53
  completed_plans: 55
  percent: 78
stopped_at: Phase 19 complete (3/3) — ready to discuss Phase 999.1
---

# State

**Status:** Ready to plan
**Current Phase:** 999.1
**Plans:** v1.0 backend complete (Phases 01–18, 50 plans); Phase 19 in progress (19-01 done)
**Last Activity:** 2026-07-15

## Deferred Items

Items acknowledged and deferred at v1.0 milestone close on 2026-07-15:

| Category | Item | Status |
|----------|------|--------|
| backlog | 999.1 payment-history-filters (status/method/date-range on GET /admin/payments, from Phase 11 D-33) | pending (v1.1 candidate) |
| verification_gap | 17.2-VERIFICATION.md multi-instance outbox duplicate-publish note | resolved doc-only in quick task 260710-eqh; single-instance topology unaffected |
| quick_task | 260710-e78-fix-4-minor-findings-from-17-2-review-md | completed (commit 0825bc2); missing status frontmatter only |
| quick_task | 260710-eqh-migrate-payment-table-kafka-producers-to | completed (commit ab09ab1); missing status frontmatter only |
| phase-doc | 17.2-inventory-settlement-idempotency-hardening + 17.3-payment-table-kafka-jackson3 | code merged into shipped backend; GSD docs orphaned (not in roadmap), collide with 17.2 outbox — re-number in v1.1 |

## Notes

- Phase 01 implemented Restaurant Menu Context catalog management and public active menu read API.
- Verification passed with focused tests and full Maven test suite on 2026-06-10.
- Quick DDD follow-up added domain repository ports and infrastructure adapters for Menu Context.
- Phase 02 implemented local Auth Context MVP: registration, local login, JWT access tokens, refresh-token lifecycle, logout, profile, route protection, and stable errors.
- Phase 03 implemented Google OAuth 2 login.
- Phase 04 implemented backend-only email verification and password reset token APIs.
- Phase 06 implemented auth hardening: Redis-backed rate limiting and lockout, audit log, refresh-session metadata, and self-service session management.
- Phase 07 implemented service-only menu order validation and price snapshot quoting for future Order/Cart flows.
- Phase 08 implemented Table Context catalog for dining areas/tables, public active listing, table validation snapshots, and minimal dev seed data.
- Phase 09 implemented an authenticated Order Context cart MVP using Menu/Table validation ports and stored snapshots.
- Phase 10 implemented submitted order persistence from cart, preserving table/line snapshots and publishing an order-created Kafka event.
- Phase 11 implemented Payment Context checkout: manual partial payments, refunds, QR payment request placeholders, order payment summaries, and payment events. Full Maven test suite passed on 2026-07-06.
- Phase 12 added for Table Operations: table sessions, occupancy, reservations, availability, order-session linkage, and table-operation events.
- Phase 12 planned with one implementation plan covering sessions, occupancy, reservations, availability, optional Order/Cart session linkage, and events.
- Phase 12 implemented and verified with focused integration coverage plus full Maven test suite on 2026-07-06.
- Phase 13 implemented Inventory Costing: ingredient master data, ingredient cost records, recipe ingredient links, recipe cost calculation, and menu margin reads. Full Maven test suite passed on 2026-07-06.
- Phase 14 implemented Inventory Management: stock-on-hand balances, immutable inventory movements (receipt/adjustment/waste/stock-count), atomic balance updates with non-negative guard, low-stock reads, and admin/staff stock APIs. Verified 8/8 success criteria; full Maven suite (119 tests) passed on 2026-07-07.
- Phase 15 implemented the Kafka order-confirmation saga (6 plans, 3 waves): order created in PENDING_CONFIRMATION, Inventory reserves stock under pessimistic lock (available = on_hand − reserved, never negative) or rejects, publishes a result event Order Context consumes to reach CONFIRMED/REJECTED. Idempotent processed-events ledgers, DefaultErrorHandler + DLT, Jackson-3-native serde (no new deps). Verification PASS 19/19; full Maven suite (138 tests) passed 2026-07-07. Code review: 0 critical, 5 warning (WR-01..05), 5 info — logged for follow-up.
- Phase 16 context gathered (2026-07-07): order-item-level preparing status + staff trigger; Inventory settles the held reservation into an actual deduction by re-resolving each line's recipe at prepare time; idempotent + clamp≥0 + DLT; new consumer applies the Phase 15 WR-01/WR-02 fixes.
- Phase 16 IMPLEMENTED (2026-07-08) after re-scope to pure inventory settlement (5 plans, 3 waves): a settle-trigger Kafka consumer that re-resolves each order line's recipe via a shared RecipeRequirementResolver (extracted from InventoryReservationService) + a new cross-context OrderLineLookupPort, locks the reservation row before ascending-ingredientId balance rows, decrements reserved+on_hand with a non-negative clamp (never throws), writes a CONSUMPTION movement directly (WR-02), and marks the reservation SETTLED when the last line settles. Dual idempotency (eventId ledger + per-(orderId,orderLineId) guard) with the WR-01 REQUIRES_NEW ledger writer; missing reservation → DLT. Jackson-3 native serde, no new deps. Verification PASS 9/9; full Maven suite 156 tests green. Producer of the settle-trigger is Phase 17 (kitchen-context).

## Accumulated Context

### Roadmap Evolution

- Phase 02 added and completed: Auth Context MVP.
- Phase 03 added and completed: Google OAuth 2 login.
- Phase 04 added and completed: Email verification and password reset.
- Phase 06 added by explicit user request, leaving Phase 05 uncreated.
- Phase 07 completed: Menu Order Validation.
- Phase 08 completed: Table Context, keeping Order/Cart deferred to Phase 09.
- Phase 09 completed: Order Cart MVP in Order Context.
- Phase 10 completed: Order Submission MVP in Order Context with Kafka order-created event publishing.
- Phase 11 added: Payment Checkout in a separate Payment Context.
- Phase 12 added: Table Operations for sessions, occupancy, and reservations.
- Phase 13 added and completed: Inventory Costing as a separate Inventory Context, keeping stock movements deferred.
- Phase 14 added: Inventory Management continues the Inventory Context with stock movements and balances while keeping automatic order deduction as a later integration decision.
- Phase 15 added and discussed (revised): reshaped from fire-and-forget deduction into an order-confirmation saga — order created in PENDING_CONFIRMATION, Inventory consumes OrderCreated, checks availability (on_hand − reserved) and reserves stock (never negative) or rejects, then publishes a result event Order Context consumes to reach CONFIRMED/REJECTED. Idempotent (processed-events ledger by eventId) with DefaultErrorHandler + DLT. Payments consumer dropped from scope.
- Phase 16 added: kitchen "đang làm" (preparing) status + event; Inventory converts the held reservation into an actual stock deduction (reserved → on_hand) — the real consumption moment, split out from Phase 15.
- Phase 16 RE-SCOPED (2026-07-07) for cleaner architecture: split into two boundaries. Phase 16 is now **inventory-reservation-settlement** — a pure Inventory settlement consumer (re-resolve line recipe → deduct reserved+on_hand, clamp≥0, CONSUMPTION audit movement, idempotent + DLT, WR-01/WR-02 fixes) reacting to a settle-trigger event. Phase 17 added: **kitchen-context** — a new bounded context (KitchenTicket aggregate, per-item preparing→ready→served→completed lifecycle, staff endpoint) that publishes the settle-trigger event and reflects fulfillment onto order status via event. The original Phase 16 plans/research/patterns were removed; Phase 16 CONTEXT rewritten and awaits re-plan.
- Phase 17.1 inserted after Phase 17: kitchen-hardening: fix Phase 17 review findings WR-01/WR-02/WR-03/IN-01/02 (URGENT)
- Phase 17.2 inserted after Phase 17: Outbox durability + messaging cleanup (remaining 17.1 review findings) (URGENT)
- Phase 18 added (2026-07-10): Order & order-item cancellation with compensation. Locked decisions — cancel window = only before kitchen (SUBMITTED/PENDING_CONFIRMATION/CONFIRMED); actors = customer(own, early) + staff/ADMIN(any in window); refund = automatic event-driven Payment refund on paid order; partial cancel = non-PREPARING items only, release reservation + recompute total. New terminal CANCELLED status. Depends on Phases 11/16/17. Awaiting plan.

- Phase 19 added (2026-07-10): VueJS admin management interface for ADMIN/STAFF operations across existing backend admin surfaces. Scope includes auth/session handling, dashboard navigation, menu, tables/table operations, inventory/stock/costing, payments/refunds, kitchen board, and order cancellation/status workflows. COMPLETED 2026-07-15 (v1.1) — MVP shipped; coverage audit found Group-A UI gaps.
- Phase 20 added (2026-07-15, v1.2): Complete admin UI — fill the Group-A UI gaps from the Phase 19 coverage audit (edit forms for menu/tables/inventory, recipe authoring + topping management, ingredient cost history + costing reads, auth session management, full reservation status transitions, role-aware affordances). Frontend-only where backend endpoints already exist; remaining backend list-endpoint gaps documented as follow-ups. Also: reorganized ROADMAP.md (removed a stale duplicate dump of v1.0 phase blocks that had been captured under ## Backlog during v1.0 milestone close).

## Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260710-e78 | Fix 4 minor findings from 17.2-REVIEW.md (WR-02 batch try/catch, WR-05 interrupt flag, IN-01 ttlDays guard, WR-03 @Lob rejection_reason) | 2026-07-10 | 0825bc2 | [260710-e78-fix-4-minor-findings-from-17-2-review-md](./quick/260710-e78-fix-4-minor-findings-from-17-2-review-md/) |
| 260710-eqh | Migrate Payment/Table Kafka producers to Jackson-3 serializer (15-01) + correct stale outbox multi-instance Javadoc (WR-01 doc-only) | 2026-07-10 | ab09ab1 | [260710-eqh-migrate-payment-table-kafka-producers-to](./quick/260710-eqh-migrate-payment-table-kafka-producers-to/) |

## Decisions

- [Phase 17.1]: Kitchen-hardening phase (17.1) scoped from Phase 17 review findings — discussed 2026-07-09, all fix-now: (WR-01) add whenComplete() callback + error logging to KafkaKitchenSettleTriggerPublisher and KafkaKitchenTicketStatusChangedPublisher so failed sends are no longer silently lost; (WR-02) persist actorId + timestamp on kitchen item advance to create an audit trail of who advanced each item; (WR-03) make KitchenStatusProjectionService fail-closed — unknown fulfillment rank logs and skips instead of getOrDefault(-1) fail-open; (IN-01/02) use existsByOrderId in KitchenTicketCreationService to absorb same-order OrderConfirmed under a new eventId instead of throwing to DLT. Inventory-side WR-01..05 remain separately deferred.
- [Phase 17.2 review]: 2026-07-10 — of 7 findings, 5 fixed. WR-02/WR-03/WR-05/IN-01 fixed in quick task 260710-e78; WR-04 fixed (dropped `${DB_PASSWORD:123456}` default → `${DB_PASSWORD}` fail-fast, local-dev value kept in `.env.example`). WR-01 (de-transactionalized `poll()` releases SKIP LOCKED locks before `publish()` → cross-instance double-publish) resolved doc-only in quick task 260710-eqh (approach b): `claimPending`/`OutboxRelay` Javadoc corrected to stop asserting cross-instance dedup safety and document at-least-once + idempotent-consumer bounding + single-instance topology. Runtime behavior unchanged; if multi-instance deployment is later planned, upgrade to a durable claim (intermediate CLAIMING status). IN-02 is advisory (no change needed).
- [Phase 15 deferred 15-01]: 2026-07-10 — RESOLVED in quick task 260710-eqh. Payment + Table Kafka producer configs migrated from Jackson-2 `JsonSerializer` to Jackson-3 `JacksonJsonSerializer` (matching order/kitchen/inventory), fixing a latent runtime failure when publishing PaymentEvent/TableOperationEvent (which carry `Instant` fields) on the Boot 4 / Jackson 3 classpath. Full Maven suite 213/213 green.
- [Phase 17.1]: Phase 17.1 scope EXPANDED (2026-07-09) to cover both Kitchen and Inventory/Order review debt. Inventory decisions (fix-now): (I-WR-01) replace inline saveAndFlush+catch idempotency idiom in InventoryReservationService and OrderConfirmationService with the REQUIRES_NEW ledger-writer pattern already introduced in Phase 16; (I-WR-02) implement a full transactional outbox — persist saga events (OrderCreated, StockReservationResult, OrderConfirmed) to an outbox table in the same DB transaction + a relay poller publishing to Kafka, so a crash between commit and Kafka send no longer strands orders in PENDING_CONFIRMATION; (I-WR-03) add whenComplete callback + error logging to KafkaInventoryStockResultPublisher and KafkaOrderEventPublisher; (I-WR-04) make rejection_reason a TEXT column and truncate describe() output to avoid overflow; (I-WR-05) switch the global producer value-serializer in application.properties from Jackson-2 JsonSerializer to Jackson-3 and re-run the full Maven suite to catch Payment/Table regressions. Combined with the 4 Kitchen fixes already decided.
