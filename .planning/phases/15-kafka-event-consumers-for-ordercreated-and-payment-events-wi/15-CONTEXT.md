# Phase 15: kafka-event-consumers (order-confirmation saga) - Context

**Gathered:** 2026-07-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Add Kafka **consumer** infrastructure (`@EnableKafka`, `ConsumerFactory`, `ConcurrentKafkaListenerContainerFactory`, consumer group-id, JSON deserializer) and wire an **order-confirmation saga** across Order Context and Inventory Context using events. The system today is produce-only (zero consumers).

**Saga:** an order is created in `PENDING_CONFIRMATION`, the existing `OrderCreated` event is consumed by Inventory, Inventory verifies ingredient availability and **reserves** stock if sufficient (or rejects), then publishes a result event that Order Context consumes to move the order to `CONFIRMED` or `REJECTED`.

**In scope:** consumer infra; order status lifecycle (`PENDING_CONFIRMATION`→`CONFIRMED`/`REJECTED`); inventory availability check + **reservation** (never negative); result event + order-side status transition; idempotency + DLT error handling.

**Out of scope (→ Phase 16):** the kitchen "đang làm" (preparing) status and the conversion of a reservation into an **actual** stock deduction (reserved → on_hand). Also out: refund/cancel → release reservation, and the `payments.events` consumer.
</domain>

<decisions>
## Implementation Decisions

### Order lifecycle & saga
- **D-01:** An order is created in status **`PENDING_CONFIRMATION`** (new `OrderStatus` value; today the enum has only `SUBMITTED`). It is not final until the saga completes. The existing after-commit `OrderCreated` producer starts the saga.
- **D-08:** `POST /orders` returns the order in `PENDING_CONFIRMATION` **synchronously**; the final `CONFIRMED`/`REJECTED` state is reached **asynchronously** and read via `GET /orders/{id}`. (Behavioral change from today's synchronous `SUBMITTED` response.)

### Inventory availability gate & reservation (never negative)
- **D-02 (revised — supersedes the earlier allow-negative decision):** Stock is **NEVER negative**. Inventory checks availability atomically as `available = on_hand − reserved ≥ required`. If sufficient → create a reservation (increment `reserved`). If insufficient → reject. There is no allow-negative path.
- **D-09:** Reservation model — Inventory gains a **`reserved` quantity** concept (a column on the stock balance row and/or a per-order reservation record keyed by `orderId`). `available = on_hand − reserved`. The reservation is **settled in Phase 16** (kitchen "đang làm" converts `reserved` → actual `on_hand` deduction). This phase only creates/holds reservations.
- **D-06 (revised):** Required quantity per order = for each `OrderCreatedEvent` line, resolve dish **and** selected topping options to recipe ingredient lines (Phase 01 recipes / Phase 13 links) × line `quantity`, unit-converted via the shared `UnitConverter` (Phase 14). A line whose dish/topping has no recipe/ingredient link contributes **zero** requirement (logged) and does not block confirmation.

### Result event & order status transition
- **D-10:** After commit, Inventory publishes a **result event** — `OrderStockConfirmed` (reservation held) or `OrderStockRejected` (with shortfall detail) — on a new topic (e.g. `inventory.order-stock-results`). Order Context **consumes** it and transitions `PENDING_CONFIRMATION` → `CONFIRMED` or `REJECTED`.
- **D-11:** Insufficient stock → order transitions to **`REJECTED`** (terminal), carrying the reason (which ingredient(s) were short). Customer/staff see it via `GET /orders`. No cart-restore and no staff-review path in this phase.

### Idempotency
- **D-03:** A **processed-events ledger** keyed by `eventId` guards **both** consumers (Inventory consuming `OrderCreated`; Order consuming the stock-result event) against double-processing under Kafka at-least-once. Reservation creation is additionally keyed by `orderId` (unique), so a replayed `OrderCreated` cannot double-reserve.

### Error handling
- **D-04:** Spring Kafka **`DefaultErrorHandler` with fixed retries + a Dead Letter Topic** (`<topic>.DLT`) on each consumer.

### Consumer & producer wiring
- **D-05:** Add consumer config mirroring the existing per-context producer config style: `@EnableKafka`, `ConsumerFactory`, `ConcurrentKafkaListenerContainerFactory`, a consumer `group-id`, and a JSON deserializer with trusted-package/type-mapping. Inventory gains a `OrderCreated` **consumer** + a result-event **producer**; Order Context gains a result-event **consumer**.

### Payments consumer
- **D-07 (revised):** The `payments.events` consumer is **out of scope** for this phase — deduction is driven by the kitchen (Phase 16), not payment. Deferred.

### Claude's Discretion
- Exact result-event topic name and schema, reservation storage shape (balance column vs. dedicated reservation table), consumer `group-id` values, retry counts/backoff, DLT topic naming, processed-events ledger granularity.
</decisions>

<specifics>
## Specific Ideas

- Inventory is the **authority** that gates order confirmation on stock availability — Order Context does not decide stock; it only reacts to Inventory's result event.
- "Never negative" is a hard invariant: confirmation reserves, it does not deduct; only Phase 16 (kitchen) reduces `on_hand`, and only against an existing reservation.
- Consumer config should mirror the existing producer configs in each context's `infrastructure/config`.
</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### This phase
- `.planning/ROADMAP.md` §"Phase 15" — phase goal/boundary (order-confirmation saga).

### Prior-phase context (locked upstream)
- `.planning/phases/14-inventory-management/14-01-SUMMARY.md` — stock balance + immutable movements, atomic balance update, non-negative guard, shared `UnitConverter` (reservation builds on this model).
- `.planning/phases/13-inventory-costing/13-01-SUMMARY.md` — recipe→ingredient links + unit-conversion factors to resolve a dish/topping to ingredients.

### Event contracts (consumer inputs)
- `src/main/java/com/example/feat1/DDD/order_context/application/event/OrderCreatedEvent.java` — `eventId`, `orderId`, `lines[]` (`dishId`, `quantity`, `selectedToppings[].toppingOptionId`).
- `src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderStatus.java` — enum to extend (`PENDING_CONFIRMATION`, `CONFIRMED`, `REJECTED`).
- `src/main/java/com/example/feat1/DDD/order_context/application/OrderSubmissionService.java` — where orders are created + `OrderCreated` published after commit (status must become `PENDING_CONFIRMATION`).

### Producer/config pattern to mirror
- `src/main/java/com/example/feat1/DDD/order_context/infrastructure/config/OrderKafkaProducerConfig.java` — style reference for new consumer/producer config.
- `src/main/resources/application.properties` §24-30 — bootstrap-servers, serializers, existing topic names.
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `InventoryStockService` + stock balance model (Phase 14): extend with a `reserved` quantity and an availability check; reuse atomic-transaction pattern.
- `UnitConverter` (Phase 14): recipe-unit → stock-unit conversion for computing required quantity.
- Recipe→ingredient resolution via Phase 13 links / `RecipeLine` (menu_context).
- Existing per-context Kafka producer configs as the structural template for new consumer/producer config.
- `OrderStatus` enum (currently `SUBMITTED` only) and `OrderEntity.status` (defaults `SUBMITTED`) — to extend and default to `PENDING_CONFIRMATION`.

### Established Patterns
- Producers publish **after commit** (`TransactionSynchronization.afterCommit`) → consumers must be idempotent (D-03).
- DDD layout: `domain` / `application` / `infrastructure(adapter|config|presentation)`.

### Integration Points
- Inventory: new `OrderCreated` listener + reservation logic + result-event producer (in `inventory_context/infrastructure`).
- Order: new result-event listener that transitions order status (in `order_context/infrastructure`); `OrderSubmissionService` sets initial status `PENDING_CONFIRMATION`.
- New processed-events ledger + reservation persistence (entities + repositories).
</code_context>

<deferred>
## Deferred Ideas

- **Phase 16 — Kitchen "đang làm" (preparing) workflow:** order-item preparing status + event; Inventory consumes it to convert a reservation into an **actual** stock deduction (`reserved` → `on_hand`). This is the real deduction moment. (Split out per this discussion.)
- **Reservation release on refund / order cancel** — no cancel flow exists; releasing a held reservation is future work.
- **`payments.events` consumer** — not the deduction trigger under this design.
- **Multi-location stock, supplier reorder automation, consumer scaling/concurrency tuning.**
</deferred>

---

*Phase: 15-kafka-event-consumers*
*Context gathered: 2026-07-07 (revised — order-confirmation saga)*
