# Phase 17: Kitchen context - Context

**Gathered:** 2026-07-08
**Status:** Ready for planning

<domain>
## Phase Boundary

Introduce a new `kitchen_context` bounded context that **owns fulfillment**. A `KitchenTicket`
aggregate is created when kitchen consumes an **`OrderConfirmed`** signal from `order_context`; it
holds a **per-item fulfillment lifecycle** (`QUEUED → preparing → ready → served → completed`). A
staff endpoint under `/admin/orders/**` (ADMIN/STAFF) advances a single item's status. On the first
transition into **preparing**, kitchen **publishes the settle-trigger event** `(orderId, orderLineId,
totalLines)` that the Phase 16 Inventory settlement consumer already reacts to. Order status reflects
fulfillment **via a kitchen→order event** (kitchen never mutates the Order aggregate). This keeps
order-taking (`order_context`), fulfillment (`kitchen_context`), and stock (`inventory_context`) as
clean, separate boundaries.

**In scope:** the `kitchen_context` bounded context (DDD domain/application/infrastructure); the
`KitchenTicket` aggregate + per-item lifecycle; a new `OrderConfirmed` event published by
`order_context`; kitchen's consumer of `OrderConfirmed`; the staff advance endpoint + a kitchen-board
read endpoint under `/admin/orders/**`; publishing the settle-trigger event on the preparing
transition (matching the existing `SettleTriggerEvent` contract); a kitchen ticket-status-changed
event consumed by `order_context`; adding `PREPARING/READY/SERVED/COMPLETED` to `OrderStatus`.

**Out of scope:** any change to inventory settlement mechanics (Phase 16 owns them — kitchen only
produces the trigger); reservation release on cancel/refund; a kitchen-board **UI** (only the read
API is in scope); multi-station routing / prep-time analytics.

**Boundary rule (locked):** Inventory re-resolves recipes itself, so the settle-trigger event stays
thin — kitchen MUST NOT carry ingredient amounts. Kitchen owns fulfillment state; `order_context`
owns order status and is a pure **consumer** of kitchen's status events.
</domain>

<decisions>
## Implementation Decisions

### Inbound trigger — how kitchen learns of a confirmed order
- **D-01:** `order_context` publishes a **new `OrderConfirmed` event** when an order transitions to
  `CONFIRMED` (inside `OrderConfirmationService.onStockResult()`, which today only mutates the entity —
  it publishes nothing). The event carries the **full line manifest** (`orderId`, and per line:
  `lineId`, `dishId`, `dishName`, `quantity`, selected toppings) so kitchen builds its `KitchenTicket`
  items **self-contained** — no runtime cross-context lookup. Mirror the existing
  `KafkaOrderEventPublisher` / `OrderEventPublisher` port + `OrderKafkaProducerConfig` pattern.
  Kitchen's `OrderConfirmed` consumer is **idempotent** (processed-events ledger, mirroring the
  established consumer pattern) — one confirmed order creates exactly one ticket.

### Per-item lifecycle & settle-trigger firing point
- **D-02:** A `KitchenTicket` holds one line-item per order line. Each item starts in **`QUEUED`** and
  advances **strictly forward, one step at a time**: `QUEUED → preparing → ready → served →
  completed`. No skipping states, no reverting. `preparing` is therefore a **deliberate staff
  action**, not an automatic on-create state.
- **D-03:** The **settle-trigger event fires exactly once**, on an item's `QUEUED → preparing`
  transition. It MUST match the existing inbound contract
  `SettleTriggerEvent(eventId, eventType, occurredAt, orderId, orderLineId, totalLines)` and be
  published to the topic Phase 16's consumer listens on (default `kitchen.settlement-trigger`).
  Because the transition is forward-only and single-step, the item can never re-enter `preparing`, so
  no second trigger is possible. `totalLines` = the ticket's total item count.

### Order-status reflection (outbound kitchen → order)
- **D-04:** Extend `OrderStatus` with `PREPARING, READY, SERVED, COMPLETED`. Kitchen publishes a
  **ticket-status-changed event** on each item transition; `order_context` **consumes** it (mirror the
  `OrderStockResultListener` / `OrderKafkaConsumerConfig` consumer pattern) and **derives** order-level
  status from the item states:
  - any item in `preparing` (and not yet all further) → `PREPARING`
  - all items `ready` → `READY`
  - all items `served` → `SERVED`
  - all items `completed` → `COMPLETED`

  The order-side consumer is **idempotent** and only advances status forward (no backward transition).
  Kitchen never writes the Order aggregate directly.

### Staff API surface (`/admin/orders/**`, ADMIN/STAFF)
- **D-05:** Provide **two** endpoints:
  1. **Advance (write):** set a single item's status, e.g.
     `PATCH /admin/orders/{orderId}/items/{itemId}/status` with the target state in the body; rejects
     illegal (non-forward / skipping) transitions with a stable error.
  2. **Kitchen board (read):** list active tickets/items for a kitchen display (items not yet
     `completed`), so staff can see the queue rather than mutate blindly.

  `/admin/orders/**` is **already secured** with `hasAnyRole("ADMIN","STAFF")` in `SecurityConfig` —
  no security change is needed for these routes.

### Claude's Discretion
- Exact event class names, Kafka topic / group-id / DLT names (kitchen owns the new `OrderConfirmed`
  and ticket-status-changed topics; the settle-trigger topic name must match Phase 16's
  `kitchen.settlement-trigger` default).
- `KitchenTicket` / ticket-item entity shape, aggregate persistence, and how order-level status is
  computed/stored on the read side.
- Request/response DTO shapes, exact URL paths, error codes, and the board endpoint's filter/sort.
- Consumer wiring details (error handler, DLT, ack mode) — reuse the Phase 15 Kafka style.
</decisions>

<specifics>
## Specific Ideas

- Kitchen is the **authority for fulfillment**; order status is a **projection** of kitchen state,
  updated only via event. Order stays a pure consumer, exactly like the Phase 15 saga's order side.
- The settle-trigger event stays **thin** (routing/identity only) — inventory re-resolves recipes.
  Kitchen must not duplicate inventory's recipe knowledge.
- Reuse the existing Kafka wiring style and **Jackson-3 native serde**, trusted packages
  `com.example.feat1.*`. **No new dependencies.**
- `preparing` being a staff-triggered action (not auto-on-confirm) is intentional: stock is deducted
  when the kitchen actually starts the item, not merely when the order is confirmed.
</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### This phase
- `.planning/ROADMAP.md` §"Phase 17: Kitchen context" — locked goal, boundaries, and the three-context
  separation (order / kitchen / inventory).

### The settle-trigger contract kitchen must produce (locked by Phase 16)
- `src/main/java/com/example/feat1/DDD/inventory_context/application/event/SettleTriggerEvent.java` —
  exact event record `(eventId, eventType, occurredAt, orderId, orderLineId, totalLines)` kitchen must
  emit; `TYPE = "SettleTrigger"`.
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaTopicConfig.java`
  — settle-trigger topic default `kitchen.settlement-trigger` (+ `.DLT`) the kitchen producer must target.
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/SettleTriggerKafkaConsumerConfig.java`
  & `.../inventory_context/infrastructure/adapter/SettleTriggerListener.java` — consumer side kitchen feeds.
- `.planning/phases/16-inventory-reservation-settlement/16-CONTEXT.md` — settle-trigger contract
  rationale (D-01) and the deferred kitchen decisions that this phase now implements.

### Order context — where OrderConfirmed is published and OrderStatus lives
- `src/main/java/com/example/feat1/DDD/order_context/application/OrderConfirmationService.java` — the
  `onStockResult()` transition to `CONFIRMED` where the new `OrderConfirmed` event must be published.
- `src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderStatus.java` — enum to extend
  with `PREPARING/READY/SERVED/COMPLETED`.
- `src/main/java/com/example/feat1/DDD/order_context/domain/port/OrderEventPublisher.java` &
  `.../infrastructure/adapter/KafkaOrderEventPublisher.java` &
  `.../infrastructure/config/OrderKafkaProducerConfig.java` — publisher port + adapter pattern to extend.
- `src/main/java/com/example/feat1/DDD/order_context/application/event/OrderCreatedEvent.java` — line
  manifest shape (`OrderLine`, `OrderTopping`) to reuse for the `OrderConfirmed` payload.
- `src/main/java/com/example/feat1/DDD/order_context/infrastructure/adapter/OrderStockResultListener.java`
  & `.../infrastructure/config/OrderKafkaConsumerConfig.java` — consumer pattern for order_context to
  consume kitchen's ticket-status-changed event.

### Kafka wiring / serde template (Phase 15 style)
- `.planning/phases/15-kafka-event-consumers-for-ordercreated-and-payment-events-wi/15-CONTEXT.md` —
  Kafka wiring, idempotency ledger, Jackson-3 native serde, DLT conventions to mirror.

### Security
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/SecurityConfig.java` — `/admin/orders/**`
  already `hasAnyRole("ADMIN","STAFF")`; `RoleEnum` (`identity_context/application/dto/RoleEnum.java`)
  defines `ADMIN/USER/MANAGER/STAFF`.
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SettleTriggerEvent` record + `kitchen.settlement-trigger` topic — the **contract kitchen must
  produce against**, already defined and consumed by Phase 16.
- `OrderCreatedEvent.OrderLine/OrderTopping` records — reuse as the `OrderConfirmed` line manifest shape.
- `OrderEventPublisher` port + `KafkaOrderEventPublisher` + `OrderKafkaProducerConfig` — extend for the
  new `OrderConfirmed` event.
- `OrderStockResultListener` + `OrderKafkaConsumerConfig` + `OrderProcessedEventEntity/Repository` —
  consumer + idempotency-ledger template for both kitchen's `OrderConfirmed` consumer and
  order_context's ticket-status-changed consumer.
- Phase 15 Kafka consumer/producer config + Jackson-3 serde as the structural template.
- `SecurityConfig` `/admin/orders/**` rule + `RoleEnum.STAFF` — auth already in place.

### Established Patterns
- Consumers are idempotent (processed-events ledger keyed by `(eventId, consumerName)`); status
  transitions guarded so terminal/legacy states don't re-transition (see `OrderConfirmationService`).
- DDD layout per context: `domain` (model/port) / `application` (service/event/dto) / `infrastructure`
  (entity/repository/adapter/config). All 7 existing contexts follow it — kitchen mirrors it.
- Kafka key = `orderId.toString()`; events are records with `eventId/eventType/occurredAt` header fields.

### Integration Points
- **Inbound:** kitchen consumes new `OrderConfirmed` (from order_context) → creates `KitchenTicket`.
- **Outbound to inventory:** kitchen publishes `SettleTriggerEvent` on `QUEUED→preparing` →
  `kitchen.settlement-trigger` (Phase 16 consumer).
- **Outbound to order:** kitchen publishes ticket-status-changed → order_context consumes → derives
  `PREPARING/READY/SERVED/COMPLETED` on the order.
</code_context>

<deferred>
## Deferred Ideas

- Kitchen-board **UI** (only the read API is in scope this phase).
- Multi-station routing, prep-time / throughput analytics, expo screens.
- Reverting an item's status to fix mistakes (forward-only chosen for D-02; revisit if staff need it —
  would require guarding against a second settle-trigger on a re-entered `preparing`).
- Reservation **release** on order cancel/refund (no cancel flow exists yet — carried from Phase 16).
- Retro-applying Phase 15 review items (WR-03/04/05) and migrating Payment/Table producers to Jackson-3.
</deferred>

---

*Phase: 17-kitchen-context*
*Context gathered: 2026-07-08*
