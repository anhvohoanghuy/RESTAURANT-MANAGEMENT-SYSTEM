# Phase 15: kafka-event-consumers - Context

**Gathered:** 2026-07-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Add Kafka **consumer** infrastructure (`@EnableKafka`, `ConsumerFactory`, `ConcurrentKafkaListenerContainerFactory`, consumer group-id, JSON deserializer) and `@KafkaListener` consumers for the already-produced `OrderCreated` (topic `orders.created`) and Payment (topic `payments.events`) events. The system today is **produce-only** (zero consumers).

The first concrete use case is **automatic inventory stock deduction** on order success — the "automatic order deduction" whose timing was explicitly deferred in Phase 14.

Fixed boundary: this phase wires consumers and the order-driven deduction use case. It does NOT add new produced events, order-cancel flows, multi-location stock, or purchasing/supplier features.
</domain>

<decisions>
## Implementation Decisions

### Deduction trigger
- **D-01:** Automatic stock deduction triggers on **`OrderCreated`** (consume topic `orders.created`), i.e. at order-submission time — NOT at payment time. Rationale: in a dine-in restaurant the kitchen consumes ingredients when the order is placed, so inventory should reflect that moment. (This resolves the deduction-timing decision deferred from Phase 14.)

### Insufficient / negative stock policy (consumer path)
- **D-02:** Auto-deduction **may drive stock negative**. It always records the movement and raises a low-stock / shortage alert for staff, and it **never fails or blocks** the already-committed order. This is a deliberate exception to Phase 14's manual-outbound rule (which blocks negative via `STOCK_INSUFFICIENT`): the order is already placed, so the truthful record is that stock was consumed even if the book balance goes negative.

### Idempotency
- **D-03:** A dedicated **processed-events ledger** table, unique on `eventId`, guards against double-deduction under Kafka at-least-once delivery. The consumer records/checks `eventId` before applying deduction; a duplicate `eventId` is skipped. Both `OrderCreatedEvent` and `PaymentEvent` already carry `eventId`.

### Error handling
- **D-04:** Spring Kafka **`DefaultErrorHandler` with fixed retries + a Dead Letter Topic** (`<topic>.DLT`). After retries are exhausted (or on non-retryable errors) the message is routed to the DLT so the partition is not blocked; DLT messages can be reprocessed later.

### Consumer infrastructure
- **D-05:** Add consumer config mirroring the existing per-context **producer** config style (`OrderKafkaProducerConfig` etc.): `@EnableKafka`, `ConsumerFactory`, `ConcurrentKafkaListenerContainerFactory`, a consumer `group-id`, and a JSON deserializer with trusted-package/type-mapping config so `OrderCreatedEvent` / `PaymentEvent` deserialize correctly.

### Deduction mechanism
- **D-06:** For each `OrderCreatedEvent` line, resolve the dish **and** its selected topping options to their recipe ingredient lines (Phase 01 recipes / Phase 13 ingredient links), multiply by the line `quantity`, convert units via the **shared `UnitConverter`** (Phase 14), and record outbound consumption movements atomically per ingredient — using a dedicated order-consumption movement type/reason so it is distinguishable from manual `WASTE`/`ADJUSTMENT_OUT`. Lines whose dish/topping has no recipe or no ingredient link are **skipped with a logged alert**, not treated as a failure.

### Payment consumer scope
- **D-07:** Build the `payments.events` consumer on the same shared infrastructure, but its stock action is minimal/deferred in this phase (deduction already happens at order time per D-01). It exists so payment events are consumable; concrete payment-driven stock behaviour (e.g. refund → stock return) is out of scope here.

### Claude's Discretion
- Exact order-consumption movement type/reason naming.
- Alert representation (reuse the Phase 14 low-stock read vs. a dedicated shortage-alert record).
- Consumer `group-id` value, retry count/backoff, and DLT topic naming.
- Whether the processed-events ledger is one shared table or per-consumer.
</decisions>

<specifics>
## Specific Ideas

- Deduction should be **truthful over defensive**: prefer recording real consumption (even negative balance + alert) over silently skipping, so shrinkage/shortage is visible to staff.
- Consumer config should look and feel like the existing producer configs already in each context's `infrastructure/config` package.
</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### This phase
- `.planning/ROADMAP.md` §"Phase 15" — phase goal and boundary.

### Prior-phase context (locked upstream decisions)
- `.planning/phases/14-inventory-management/14-01-SUMMARY.md` — stock balance + immutable movements, atomic balance update, non-negative guard, shared `UnitConverter`.
- `.planning/phases/14-inventory-management/14-VERIFICATION.md` — verified stock behaviour the consumer must reuse.
- `.planning/phases/13-inventory-costing/13-01-SUMMARY.md` — recipe→ingredient links and unit-conversion factors used to resolve a dish to its ingredients.

### Event contracts (consumer inputs)
- `src/main/java/com/example/feat1/DDD/order_context/application/event/OrderCreatedEvent.java` — payload: `eventId`, `orderId`, `lines[]` (`dishId`, `quantity`, `selectedToppings[].toppingOptionId`).
- `src/main/java/com/example/feat1/DDD/payment_context/application/event/PaymentEvent.java` — payload: `eventId`, `eventType` (`PaymentRecorded`/`PaymentRefunded`/`ORDER_PAYMENT_COMPLETED`), `orderId`.

### Producer/config pattern to mirror
- `src/main/java/com/example/feat1/DDD/order_context/infrastructure/config/OrderKafkaProducerConfig.java` — style reference for the new consumer config.
- `src/main/resources/application.properties` §24-30 — `spring.kafka.bootstrap-servers`, serializers, topic names (`orders.created`, `payments.events`).
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `InventoryStockService.recordMovement(...)` (Phase 14): atomic movement + balance update — the consumer should call this (or a sibling path) rather than writing balances directly.
- `UnitConverter` (Phase 14): shared unit normalization/conversion for recipe-unit → stock-unit.
- `InventoryMovementType` (Phase 14): extend/reuse for an order-consumption movement type.
- Recipe→ingredient resolution via Phase 13 costing links + `RecipeLine` (menu_context) / `RecipeCostingSnapshot` (inventory_context).
- Existing per-context Kafka **producer** configs as the structural template for the consumer config.

### Established Patterns
- Producers publish **after commit** via `TransactionSynchronization.afterCommit` (`OrderSubmissionService`, `PaymentService`) — consumers must therefore be idempotent and tolerant of at-least-once redelivery (D-03).
- DDD context layout: `domain` / `application` / `infrastructure(adapter|config|presentation)`.

### Integration Points
- New consumer lives in `inventory_context/infrastructure` (adapter/listener + config).
- Reads `OrderCreatedEvent` from `orders.created`; calls into `InventoryStockService`.
- New processed-events ledger persistence (entity + repository) in `inventory_context/infrastructure`.
</code_context>

<deferred>
## Deferred Ideas

- **Stock return on refund / order cancel** — no order-cancel flow exists yet; `PaymentRefunded` → stock return is a separate future phase.
- **Payment-triggered deduction option** (deduct at payment instead of/along with order) — D-01 chose order-time; a configurable dual-trigger is future work.
- **Multi-location stock** and supplier/purchasing reorder automation on low-stock alerts.
- **Consumer scaling / concurrency tuning** (partitions, container concurrency) beyond a working default.
</deferred>

---

*Phase: 15-kafka-event-consumers*
*Context gathered: 2026-07-07*
