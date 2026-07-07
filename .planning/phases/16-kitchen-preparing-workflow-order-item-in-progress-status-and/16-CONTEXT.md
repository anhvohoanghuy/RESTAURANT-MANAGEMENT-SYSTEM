# Phase 16: Kitchen preparing workflow — settle reservations into actual deductions - Context

**Gathered:** 2026-07-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Add a kitchen "đang làm" (in-progress / preparing) transition at the **order-item level** for a
CONFIRMED order. When a kitchen staff member marks an order item as preparing, the system
publishes an event; Inventory consumes it and converts that item's **held reservation** into an
**actual stock deduction** (`reserved` ↓ and `quantity_on_hand` ↓ for the item's ingredients),
keeping stock **non-negative**. This is the real consumption moment, split out from the Phase 15
confirmation saga.

**In scope:** order-item preparing status, an order-level `PREPARING` status, a staff-triggered
transition + event, Inventory settlement consumer (reservation → actual deduction), idempotency,
DLT, non-negative invariant.

**Out of scope (deferred):** order lifecycle beyond preparing (READY / SERVED / COMPLETED),
reservation release on cancel/refund, changing the Phase 15 reservation storage shape, the
`payments.events` consumer.
</domain>

<decisions>
## Implementation Decisions

### Preparing granularity & order status
- **D-01:** Preparing status is at the **order-item (order line) level** — each `OrderLineEntity`
  gains a preparing status; kitchen staff mark individual items as "đang làm". (Matches the phase
  name "order item in-progress status".)
- **D-06:** Add **`OrderStatus.PREPARING`** (order level). The order transitions
  **`CONFIRMED` → `PREPARING`** when its **first** line starts preparing. No `COMPLETED`/`SERVED`
  status in this phase — later lifecycle states are deferred to a future phase.

### Trigger
- **D-02:** The transition is triggered by a **new staff API endpoint** (e.g.
  `POST /orders/{id}/items/{lineId}/prepare` or a PATCH status), **role-gated** to kitchen/staff,
  and only valid when the order is `CONFIRMED` (or already `PREPARING`) and the target line has not
  yet been settled. The preparing event is published **after commit** (mirrors the Phase 15
  after-commit producer pattern → consumer must be idempotent).

### Per-item settlement mechanics
- **D-05:** The Phase 15 reservation (`StockReservationEntity`) stores **aggregate** reserved base
  quantity **per ingredient for the whole order**, not per order-line. To settle per item,
  **re-resolve the recipe requirement for that specific `OrderLine` at prepare time**, reusing the
  exact Phase 15 recipe-resolution path (dish + selected toppings → ingredient lines × line
  quantity, unit-converted via the shared `UnitConverter`). Decrement `quantity_on_hand` **and** the
  balance `reserved_quantity` by that line's per-ingredient amounts. Mark the whole reservation
  `SETTLED` only when the **last** line has been prepared/settled. **No change to the Phase 15
  reservation model.** Accepted risk: a recipe edited between confirm and prepare yields a
  slightly different requirement — this is tolerated and **logged**.

### Settlement correctness & non-negative invariant
- **D-03:** Settlement is **idempotent** and enforces `quantity_on_hand ≥ 0`:
  - Replay / already-settled line → **no-op** (guarded by the processed-events ledger AND a
    per-`(orderId, orderLineId)` settlement guard so a single item cannot be double-deducted).
  - If `quantity_on_hand < reserved` for an ingredient at settle time (should not happen — Phase 15
    guaranteed availability at reserve time) → **clamp `on_hand` to 0**, log/flag the anomaly, do not
    go negative.
  - Missing HELD reservation for the order → route the record to the **DLT** (do not silently
    swallow).

### Consumer robustness (apply Phase 15 code-review fixes here)
- **D-04:** The **new settlement consumer** applies the fixes surfaced in the Phase 15 review
  (`15-REVIEW.md`):
  - **WR-01:** isolate the idempotency-ledger insert in a **`REQUIRES_NEW`** transaction so a
    duplicate-key collision does not mark the whole business transaction rollback-only.
  - **WR-02 / audit:** record an **`InventoryStockMovementEntity`** (consumption/issue type) for each
    settlement so the actual deduction is auditable via the Phase 14 movement ledger.
  - Applying the same fix retroactively to the Phase 15 consumers is a **separate** hardening task,
    not part of this phase.

### Claude's Discretion
- Exact endpoint shape/verb, event name (`OrderItemPreparingEvent` / `OrderPreparingEvent`) and new
  topic name, consumer `group-id`, retry counts/backoff, DLT topic naming.
- Whether the order line's preparing status is a new enum on `OrderLineEntity` or a boolean/timestamp.
- `ReservationStatus` additions (e.g. `SETTLED`) and how per-line settlement progress is tracked
  (line status vs. a settled-lines set on the reservation).
- Movement type value used for the deduction.
</decisions>

<specifics>
## Specific Ideas

- Inventory remains the **authority** for stock; Order Context only signals "item is being prepared"
  and Inventory decides the deduction against the existing reservation.
- "Never negative" stays a hard invariant — settlement deducts against a prior HELD reservation and
  clamps at zero rather than going negative.
- Reuse the Phase 15 Kafka wiring style (Jackson-3 native `JacksonJsonDeserializer` with
  `com.example.feat1.*` trusted packages, `ErrorHandlingDeserializer`, `AckMode.RECORD`,
  `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`, `NewTopic` beans). **No new dependencies.**
</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### This phase
- `.planning/ROADMAP.md` §"Phase 16" — phase goal/boundary (settle reservations into actual deductions).

### Upstream context (locked)
- `.planning/phases/15-kafka-event-consumers-for-ordercreated-and-payment-events-wi/15-CONTEXT.md` — the confirmation-saga decisions (D-01..D-11): reservation model, non-negative invariant, idempotency, Kafka wiring, Jackson-3 serde.
- `.planning/phases/15-kafka-event-consumers-for-ordercreated-and-payment-events-wi/15-03-SUMMARY.md` — `InventoryReservationService`: the exact recipe-resolution path to reuse per line at prepare time; `CONSUMER_NAME` idempotency pattern.
- `.planning/phases/15-kafka-event-consumers-for-ordercreated-and-payment-events-wi/15-REVIEW.md` — WR-01 (`REQUIRES_NEW` idempotency) and WR-02 (durability/audit) findings to apply in the new consumer.

### Code to read / extend
- `src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderStatus.java` — add `PREPARING`.
- `src/main/java/com/example/feat1/DDD/order_context/infrastructure/entity/OrderEntity.java` & `OrderLineEntity.java` — order/line status to extend; order transitions CONFIRMED→PREPARING.
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/StockReservationEntity.java` — HELD reservation with per-ingredient base-quantity lines; add settled state.
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/InventoryStockBalanceEntity.java` — `quantity_on_hand` + `reserved_quantity` to decrement.
- `src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryStockService.java` & `infrastructure/entity/InventoryStockMovementEntity.java` — Phase 14 movement/deduction mechanism to record the actual consumption.
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaConsumerConfig.java` & `order_context/infrastructure/config/OrderKafkaProducerConfig.java` — Kafka wiring style to mirror.

### Event contract inputs
- `src/main/java/com/example/feat1/DDD/order_context/application/event/OrderStockResultEvent.java` — Phase 15 result-event contract style to mirror for the new preparing event.
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- Phase 15 `InventoryReservationService` recipe-resolution (dish + toppings → ingredient base
  quantities via `UnitConverter`) — re-run **per order line** at prepare time (D-05).
- `StockReservationEntity` (unique `orderId`, `ReservationStatus.HELD`, per-ingredient `ReservationLine`).
- `InventoryStockBalanceEntity` (`quantity_on_hand`, `reserved_quantity`) + `InventoryStockService` /
  `InventoryStockMovementEntity` (Phase 14 atomic deduction + movement audit).
- Processed-events ledger pattern (`InventoryProcessedEventEntity` / `OrderProcessedEventEntity`) —
  reuse for the new consumer, with the WR-01 `REQUIRES_NEW` fix.
- Phase 15 Kafka consumer/producer config + Jackson-3 serde as the structural template.

### Established Patterns
- Producers publish **after commit**; consumers are idempotent (ledger + business-key guard).
- DDD layout: `domain` / `application` / `infrastructure(adapter|config|presentation)`.

### Integration Points
- Order Context: new staff endpoint + line/order status transition + after-commit preparing producer.
- Inventory Context: new preparing-event consumer → per-line settlement (decrement on_hand+reserved,
  record movement, mark reservation SETTLED when complete).
- New processed-events guard + per-(orderId, orderLineId) settlement guard.
</code_context>

<deferred>
## Deferred Ideas

- Order lifecycle beyond preparing: `READY` / `SERVED` / `COMPLETED` statuses and their events.
- Reservation **release** on order cancel / refund (no cancel flow exists yet).
- Retroactively applying WR-01/WR-02 fixes to the **Phase 15** consumers (separate hardening task).
- Fixing the remaining Phase 15 review items (WR-03 fire-and-forget send, WR-04 `rejection_reason`
  length, WR-05 global Jackson-2 producer serializer).
- Migrating Payment/Table producers to Jackson-3 (pre-existing defect logged in `deferred-items.md`).
- Multi-location stock, supplier reorder automation, consumer scaling/concurrency tuning.
</deferred>

---

*Phase: 16-kitchen-preparing-workflow-order-item-in-progress-status-and*
*Context gathered: 2026-07-07*
