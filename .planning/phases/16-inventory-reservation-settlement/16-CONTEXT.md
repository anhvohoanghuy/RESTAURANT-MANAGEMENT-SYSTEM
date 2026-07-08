# Phase 16: Inventory reservation settlement - Context

**Gathered:** 2026-07-07 (re-scoped 2026-07-07 after the kitchen-context boundary decision)
**Status:** Ready for planning

<domain>
## Phase Boundary

Add an **Inventory settlement consumer** that converts a held reservation into an **actual stock
deduction** (`reserved` → `quantity_on_hand` decreases, never negative) when it receives a
**settle-trigger event**. This is a **pure inventory concern**: this phase does NOT own the trigger,
does NOT add any staff endpoint, and does NOT touch order status — those belong to the new
`kitchen_context` in **Phase 17**, which will publish the settle-trigger event this consumer reacts to.

**Re-scope note:** The original Phase 16 discussion bundled the kitchen "preparing" UX (order-item
status, staff endpoint, order-level `PREPARING`) with the inventory settlement. That kitchen/fulfillment
concern has been split into its own bounded context (**Phase 17: kitchen-context**) for a cleaner
architecture. The original discussion decisions D-01 (order-item preparing status), D-02 (staff
endpoint), and D-06 (order → PREPARING) **move to Phase 17**. This phase keeps only the inventory
settlement mechanics (originally D-03/D-04/D-05).

**In scope:** the settlement consumer + its inbound event contract, per-line recipe re-resolution,
`reserved`+`on_hand` deduction with non-negative clamp under a pessimistic lock, CONSUMPTION audit
movement, reservation `SETTLED` transition, idempotency, DLT.

**Out of scope (→ Phase 17 kitchen_context):** KitchenTicket aggregate, per-item fulfillment
lifecycle (preparing/ready/served/completed), the staff endpoint that advances an item, publishing
the settle-trigger event, and reflecting fulfillment onto order status.

**Dependency note:** The **producer** of the settle-trigger event is Phase 17. Until Phase 17 lands,
this consumer has no live upstream and is exercised via unit/slice tests (mirrors the wave-by-wave
build of Phase 15). This phase defines the **inbound event contract** it consumes so Phase 17 can
produce matching events.
</domain>

<decisions>
## Implementation Decisions

### Settle-trigger event contract (inbound, owned here)
- **D-01:** Define a minimal inbound settle-trigger event carrying **`(eventId, orderId, orderLineId, totalLines)`**.
  Inventory re-resolves the line's recipe itself — the event does **not** carry ingredient amounts
  (kitchen must not duplicate inventory's recipe knowledge). `totalLines` lets Inventory detect the
  "last line" for the reservation `SETTLED` transition, since `StockReservationEntity` has no line
  manifest. Jackson-3-native serde, trusted packages `com.example.feat1.*`. No new dependencies.

### Per-line settlement mechanics
- **D-02:** On each settle-trigger, **re-resolve that single `OrderLine`'s recipe** at settle time,
  reusing the exact Phase 15 recipe-resolution path (dish + selected toppings → ingredient base
  quantities via the shared `UnitConverter`). Decrement `quantity_on_hand` **and** `reserved_quantity`
  by that line's per-ingredient amounts, under a canonical **sorted-ascending-ingredient-id pessimistic
  lock** (reuse the Phase 15 loop, subtract instead of add). Accepted risk: a recipe edited between
  confirm and settle yields a slightly different requirement — tolerated and **logged**.
- **D-03:** Enforce the **non-negative invariant**: if `quantity_on_hand < reserved` for an ingredient
  at settle time (should not happen — Phase 15 guaranteed availability at reserve time), **clamp
  `on_hand` to 0** and log the anomaly; never go negative.
- **D-04:** Mark `StockReservationEntity` → **`SETTLED`** only when the **last** line settles
  (tracked via a per-`(orderId, orderLineId)` settlement record counted against `totalLines`).

### Idempotency & error handling
- **D-05:** Idempotent settlement: a processed-events ledger keyed by `eventId` (mirror
  `InventoryProcessedEventEntity`) **plus** a per-`(orderId, orderLineId)` settlement guard so one
  item cannot be double-deducted on redelivery. A **missing HELD reservation** routes the record to
  the **DLT** (do not silently swallow). Kafka wiring mirrors Phase 15: `@EnableKafka`, typed
  `ConsumerFactory` with `ErrorHandlingDeserializer` → Jackson-3 `JacksonJsonDeserializer`,
  `AckMode.RECORD`, `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`, `NewTopic` beans for the
  settle topic + its DLT.

### Consumer robustness (apply Phase 15 code-review fixes)
- **D-06:** Apply the Phase 15 review fixes in this **new** consumer:
  - **WR-01:** isolate the idempotency-ledger insert in a dedicated bean method annotated
    `@Transactional(propagation = REQUIRES_NEW)` — do NOT copy the Phase-15 `saveAndFlush` + catch
    idiom that leaves the business transaction rollback-only.
  - **WR-02:** record an `InventoryStockMovementEntity` (CONSUMPTION type) per settlement so the actual
    deduction is auditable via the Phase 14 movement ledger.
  - Retro-applying these fixes to the Phase 15 consumers remains a **separate** hardening task.

### Claude's Discretion
- Exact event/topic/group-id/DLT names, movement type value, the settlement-record entity shape and
  how `totalLines` is compared, retry counts/backoff.
</decisions>

<specifics>
## Specific Ideas

- Inventory stays the **authority** for stock; it reacts to a settle-trigger from kitchen and decides
  the deduction against the existing reservation — it does not know about fulfillment UX.
- Inventory **re-resolves recipe itself**; the kitchen event stays thin (`orderId, orderLineId, totalLines`).
- Reuse the Phase 15 Kafka wiring style and Jackson-3 native serde. **No new dependencies.**
</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### This phase
- `.planning/ROADMAP.md` §"Phase 16" — re-scoped goal (inventory settlement) and §"Phase 17" (kitchen context that produces the trigger).

### Upstream context (locked)
- `.planning/phases/15-kafka-event-consumers-for-ordercreated-and-payment-events-wi/15-CONTEXT.md` — reservation model, non-negative invariant, idempotency, Kafka wiring, Jackson-3 serde.
- `.planning/phases/15-kafka-event-consumers-for-ordercreated-and-payment-events-wi/15-03-SUMMARY.md` — `InventoryReservationService`: the recipe-resolution path to reuse per line; `CONSUMER_NAME` idempotency pattern.
- `.planning/phases/15-kafka-event-consumers-for-ordercreated-and-payment-events-wi/15-REVIEW.md` — WR-01 (`REQUIRES_NEW`) and WR-02 (audit movement) findings to apply.

### Code to read / extend
- `src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationService.java` — recipe-resolution + sorted-id pessimistic-lock loop to reuse (subtract instead of add).
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/StockReservationEntity.java` — HELD reservation with per-ingredient lines; add `SETTLED`.
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/InventoryStockBalanceEntity.java` — `quantity_on_hand` + `reserved_quantity` to decrement.
- `src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryStockService.java` & `infrastructure/entity/InventoryStockMovementEntity.java` — Phase 14 movement mechanism to record CONSUMPTION.
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaConsumerConfig.java` & `infrastructure/entity/InventoryProcessedEventEntity.java` — Kafka wiring + idempotency ledger to mirror (with WR-01 fix).
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- Phase 15 `InventoryReservationService` recipe-resolution + sorted-ingredient-id pessimistic-lock loop.
- `StockReservationEntity` (per-order HELD, per-ingredient `ReservationLine`) — add `SETTLED`.
- `InventoryStockBalanceEntity` + `InventoryStockService` / `InventoryStockMovementEntity` (Phase 14 deduction + audit).
- `InventoryProcessedEventEntity` idempotency ledger — reuse with the WR-01 `REQUIRES_NEW` fix.
- Phase 15 Kafka consumer config + Jackson-3 serde as the structural template.

### Established Patterns
- Consumers are idempotent (ledger + business-key guard); DDD layout domain/application/infrastructure.

### Integration Points
- Inventory only: new settle-trigger consumer → per-line settlement (decrement on_hand+reserved,
  record CONSUMPTION movement, mark reservation SETTLED when last line settles), new inbound event
  contract, new processed-events + per-`(orderId, orderLineId)` guard.
</code_context>

<deferred>
## Deferred Ideas

### → Phase 17 (kitchen_context) — the original discussion's kitchen decisions
- Order-item preparing status + per-item fulfillment lifecycle (preparing → ready → served → completed).
- Staff endpoint under `/admin/orders/**` (ADMIN/STAFF) that advances an item's status.
- `KitchenTicket` aggregate derived from `OrderConfirmed`; publishing the settle-trigger event this phase consumes.
- Order status reflecting fulfillment (`CONFIRMED → PREPARING`) via event (kitchen does not mutate the Order aggregate).

### Later / other phases
- Reservation **release** on order cancel / refund (no cancel flow exists yet).
- Retro-applying WR-01/WR-02 to the Phase 15 consumers; remaining Phase 15 review items (WR-03/04/05).
- Migrating Payment/Table producers to Jackson-3 (pre-existing defect in `../15-.../deferred-items.md`).
- Multi-location stock, supplier reorder automation, consumer scaling/concurrency tuning.
</deferred>

---

*Phase: 16-inventory-reservation-settlement*
*Context re-scoped: 2026-07-07 (kitchen concern split to Phase 17)*
