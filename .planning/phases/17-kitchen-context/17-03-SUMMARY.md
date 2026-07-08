---
phase: 17-kitchen-context
plan: 03
subsystem: kitchen_context (Kafka inbound wiring + ticket creation)
tags: [kafka, kitchen, idempotency, consumer, ddd]
dependency-graph:
  requires: [17-01, 17-02]
  provides:
    - "KitchenKafkaTopicConfig (orders.confirmed + kitchen.ticket-status-changed NewTopic beans)"
    - "OrderConfirmedKafkaConsumerConfig (poison-pill-safe consumer wiring)"
    - "KitchenTicketCreationService.onOrderConfirmed"
    - "OrderConfirmedListener"
  affects:
    - "17-04/17-05 (KitchenTicketAdvanceService will build on the same ticket/item entities and reuse the kitchen-prefixed bean-naming convention)"
tech-stack:
  added: []
  patterns:
    - "Inline idempotency ledger (existsByEventIdAndConsumerName pre-check + saveAndFlush + DataIntegrityViolationException swallow)"
    - "Kitchen-prefixed Kafka bean names to avoid app-wide collisions"
    - "ErrorHandlingDeserializer + TRUSTED_PACKAGES + VALUE_DEFAULT_TYPE + USE_TYPE_INFO_HEADERS=false poison-pill-safe consumer wiring"
    - "Thin one-line @KafkaListener delegate to application service"
key-files:
  created:
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/KitchenKafkaTopicConfig.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/OrderConfirmedKafkaConsumerConfig.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketCreationService.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/adapter/OrderConfirmedListener.java
    - src/test/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/OrderConfirmedKafkaConsumerConfigTest.java
    - src/test/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketCreationServiceTest.java
  modified: []
decisions:
  - "Kitchen's own NewTopic beans are limited to orders.confirmed(+DLT) and kitchen.ticket-status-changed(+DLT); the already-declared kitchen.settlement-trigger topic (owned by InventoryKafkaTopicConfig) is intentionally not redeclared here, matching the plan's anti-pattern guard."
  - "All kitchen-side consumer/DLT beans are kitchen-prefixed (kitchenDltKafkaTemplate, orderConfirmedConsumerFactory, orderConfirmedErrorHandler, orderConfirmedKafkaListenerContainerFactory) to avoid app-wide bean-name collisions with order/inventory consumer configs."
metrics:
  duration: "~25 min"
  completed: 2026-07-08
---

# Phase 17 Plan 03: Kitchen inbound Kafka wiring + idempotent ticket creation Summary

Wired kitchen_context's consumer half of D-01: declared kitchen's own Kafka topics
(`orders.confirmed` + DLT, `kitchen.ticket-status-changed` + DLT) without redeclaring the
already-owned settle-trigger topic, added a poison-pill-safe `OrderConfirmedEvent` consumer
config, a thin `OrderConfirmedListener` delegate, and an idempotent
`KitchenTicketCreationService` that turns exactly one `OrderConfirmedEvent` into exactly one
`KitchenTicketEntity` with all `KitchenTicketItemEntity` rows built in a single pass from the
event's line manifest.

## What Was Built

**Task 1 — Kitchen topic config + poison-pill-safe OrderConfirmed consumer config**
(commits `43a1f05`, `15b8cb2`):
- `KitchenKafkaTopicConfig`: `NewTopic` beans for `orders.confirmed`/`orders.confirmed.DLT` and
  `kitchen.ticket-status-changed`/`kitchen.ticket-status-changed.DLT` only — verified no
  `settleTriggerTopic` redeclaration (grep-clean, including javadoc wording adjusted so the
  literal string doesn't appear at all).
- `OrderConfirmedKafkaConsumerConfig`: cloned `OrderKafkaConsumerConfig` retyped to
  `OrderConfirmedEvent`, `TRUSTED_PACKAGES = com.example.feat1.DDD.order_context.application.event`,
  `VALUE_DEFAULT_TYPE = OrderConfirmedEvent`, `USE_TYPE_INFO_HEADERS=false`,
  `ErrorHandlingDeserializer`, `DefaultErrorHandler` with `FixedBackOff(1000L, 3L)` +
  `DeserializationException` marked non-retryable, `AckMode.RECORD`. All beans kitchen-prefixed:
  `kitchenDltKafkaTemplate`, `orderConfirmedConsumerFactory`, `orderConfirmedErrorHandler`,
  `orderConfirmedKafkaListenerContainerFactory`.
- Broker-free `OrderConfirmedKafkaConsumerConfigTest` (3 tests) mirrors
  `SettleTriggerKafkaConsumerConfigTest`: asserts deserializer wiring, trusted packages, default
  type, `USE_TYPE_INFO_HEADERS=false`, `AckMode.RECORD`, and that `DeserializationException` is
  non-retryable.

**Task 2 — Idempotent KitchenTicketCreationService + thin OrderConfirmedListener** (TDD;
commits `2146f5f` RED, `b580c45` GREEN):
- `KitchenTicketCreationService.onOrderConfirmed(OrderConfirmedEvent)`: `CONSUMER_NAME =
  "kitchen-order-confirmed"`; follows the inline idempotency ledger pattern verbatim
  (`existsByEventIdAndConsumerName` pre-check, then `saveAndFlush` the
  `KitchenProcessedEventEntity` inside try/catch on `DataIntegrityViolationException`). After the
  ledger write, builds ONE `KitchenTicketEntity` and, in a single pass over `event.lines()`, all
  `KitchenTicketItemEntity` rows (orderLineId, dishId, dishName, quantity, topping snapshots,
  status defaulting to `QUEUED`), wires the bidirectional `ticket`/`items` link, then
  `kitchenTicketRepository.save(ticket)`. Items are never appended after the initial save.
- `OrderConfirmedListener`: one-line `@KafkaListener` delegate (topic
  `${order.events.order-confirmed-topic:orders.confirmed}`, group-id
  `${kitchen.order-confirmed.consumer.group-id:kitchen-order-confirmed}`, container factory
  `orderConfirmedKafkaListenerContainerFactory`) calling
  `creationService.onOrderConfirmed(event)` only — no repository/ledger logic in the adapter.
- `KitchenTicketCreationServiceTest` (3 tests): first delivery creates one ticket with item count
  == manifest line count and correct field/topping mapping; redelivery of the same event creates
  no duplicate ticket (`save` called once across two invocations); a concurrent duplicate ledger
  insert (mocked `DataIntegrityViolationException` from `saveAndFlush`) is swallowed and creates
  no ticket.

## Verification

- `./mvnw -o test -Dtest=OrderConfirmedKafkaConsumerConfigTest` — 3/3 passing
- `./mvnw -o test -Dtest=KitchenTicketCreationServiceTest` — 3/3 passing
- `grep -R "settleTriggerTopic" .../KitchenKafkaTopicConfig.java` — no matches (confirmed after a
  javadoc wording tweak so even the doc comment doesn't contain the literal string)
- `grep -R "kitchenDltKafkaTemplate" src/main/java/.../kitchen_context` — present, kitchen-prefixed
  DLT template confirmed
- Full suite: `./mvnw -o clean test` — 163/163 tests passing (no regressions)

## TDD Gate Compliance

Task 2 (`tdd="true"`) followed the RED/GREEN cycle: RED commit `2146f5f` (test added before the
service class existed — compilation failure confirmed as the RED signal since the class truly
did not exist yet), GREEN commit `b580c45` (implementation added, all 3 tests pass). No
REFACTOR commit was needed — the GREEN implementation matched the intended shape with no
follow-up cleanup required.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - blocking/verification-alignment] Reworded a javadoc comment in
`KitchenKafkaTopicConfig` to avoid the literal string `settleTriggerTopic`**
- **Found during:** Task 1, running the plan's `<verification>` grep check
  (`grep -R "settleTriggerTopic" .../KitchenKafkaTopicConfig.java` must return nothing).
- **Issue:** The initial javadoc explaining the anti-pattern guard mentioned
  `{@code settleTriggerTopic}`/`{@code settleTriggerDltTopic}` by name (the bean names owned by
  `InventoryKafkaTopicConfig`), which made the plan's own verification grep match a doc comment
  even though no bean with that name was actually declared in this file.
- **Fix:** Reworded the comment to describe "the kitchen.settlement-trigger topic's NewTopic
  beans" instead of naming the bean methods literally, preserving the documentation intent while
  making the verification grep pass cleanly.
- **Files modified:** `src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/KitchenKafkaTopicConfig.java`
- **Commit:** `15b8cb2`

No other deviations — plan executed as written otherwise.

## Known Stubs

None. Both files created in this plan are fully wired (no placeholder/empty-return code paths).

## Threat Flags

None — all new surface (the `orders.confirmed` consumer boundary) was already covered by the
plan's `<threat_model>` (T-17-05, T-17-06, T-17-07), and this plan mitigates each entry as
specified (poison-pill-safe deserialization, idempotency ledger + unique `order_id` constraint,
non-retryable `DeserializationException` routed to DLT).

## Self-Check: PASSED

- FOUND: src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/KitchenKafkaTopicConfig.java
- FOUND: src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/OrderConfirmedKafkaConsumerConfig.java
- FOUND: src/main/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketCreationService.java
- FOUND: src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/adapter/OrderConfirmedListener.java
- FOUND: src/test/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/OrderConfirmedKafkaConsumerConfigTest.java
- FOUND: src/test/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketCreationServiceTest.java
- FOUND commit 43a1f05
- FOUND commit 2146f5f
- FOUND commit b580c45
- FOUND commit 15b8cb2
