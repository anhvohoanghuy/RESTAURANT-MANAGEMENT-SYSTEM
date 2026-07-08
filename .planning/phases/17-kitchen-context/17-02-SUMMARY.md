---
phase: 17-kitchen-context
plan: 02
subsystem: kitchen_context (domain model, JPA aggregate, repositories)
tags: [kitchen, jpa, ddd, pessimistic-lock, idempotency-ledger]
dependency-graph:
  requires: []
  provides:
    - kitchen_context domain model (KitchenItemStatus, KitchenDomainException)
    - kitchen_context JPA aggregate (KitchenTicketEntity, KitchenTicketItemEntity, KitchenTicketItemToppingSnapshot, KitchenProcessedEventEntity)
    - kitchen_context repositories (KitchenTicketRepository, KitchenTicketItemRepository, KitchenProcessedEventRepository)
  affects:
    - Future kitchen plans (17-03 consumer, 17-05 advance service, 17-06/07 board+controller) build directly on these entities/repos
tech-stack:
  added: []
  patterns:
    - Full-entity child (not @Embeddable) for independently lockable/URL-addressable rows, mirroring OrderLineEntity
    - Dual-key pessimistic lock query (orderId + id) closing both IDOR and double-transition races, mirroring OrderLineLookupAdapter + StockReservationRepository
    - Per-context idempotency ledger table with unique (event_id, consumer_name), mirroring OrderProcessedEventEntity
key-files:
  created:
    - src/main/java/com/example/feat1/DDD/kitchen_context/domain/model/KitchenItemStatus.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/domain/model/KitchenDomainException.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/entity/KitchenTicketEntity.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/entity/KitchenTicketItemEntity.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/entity/KitchenTicketItemToppingSnapshot.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/entity/KitchenProcessedEventEntity.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/repository/KitchenTicketRepository.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/repository/KitchenTicketItemRepository.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/repository/KitchenProcessedEventRepository.java
  modified: []
decisions:
  - "KitchenTicketItemEntity modeled as a full @Entity (not @Embeddable/@ElementCollection), matching the plan's resolved Open Question #2, so each item is independently lockable and URL-addressable"
  - "lockByOrderIdAndItemId is a single dual-key query closing both the double-settle-trigger race (PESSIMISTIC_WRITE) and the IDOR threat (item must belong to a ticket for the path's orderId) in one predicate"
  - "KitchenTicketItemToppingSnapshot drops the price column present on OrderLineToppingSnapshot since kitchen has no pricing need"
metrics:
  duration: "~25 min"
  completed: 2026-07-08
---

# Phase 17 Plan 02: Kitchen context foundation — domain model, JPA aggregate, repositories Summary

Created the `kitchen_context` bounded-context skeleton: the QUEUED->PREPARING->READY->SERVED->COMPLETED
fulfillment lifecycle enum, a stable `KitchenDomainException`, the full JPA aggregate
(`KitchenTicketEntity` + full-entity `KitchenTicketItemEntity` + `KitchenTicketItemToppingSnapshot` +
`KitchenProcessedEventEntity` ledger), and the three repositories — including the dual-key
pessimistic-lock item lookup that later kitchen plans depend on to close the double-settle-trigger
race and the cross-order IDOR threat.

## What Was Built

### Task 1: Domain model
- `KitchenItemStatus` — five-value enum (`QUEUED, PREPARING, READY, SERVED, COMPLETED`) with a
  code comment documenting that declaration order is load-bearing for the forward-only ordinal guard
  a later plan (17-05) will build on top of it.
- `KitchenDomainException extends AppException` — three code constants
  (`KITCHEN_TICKET_NOT_FOUND`, `KITCHEN_ITEM_NOT_FOUND`, `KITCHEN_TRANSITION_INVALID`) and matching
  static factories `ticketNotFound()`/`itemNotFound()` (`HttpStatus.NOT_FOUND`) and
  `transitionInvalid()` (`HttpStatus.BAD_REQUEST`, matching `TableDomainException.reservationStatusInvalid`).

### Task 2: JPA aggregate
- `KitchenTicketEntity` (`kitchen_tickets`) — aggregate root, unique constraint on `order_id`
  enforcing one ticket per confirmed order, cascading `@OneToMany` `items` list.
- `KitchenTicketItemEntity` (`kitchen_ticket_items`) — full entity (not `@Embeddable`), `@ManyToOne`
  back to the ticket, `orderLineId` (carried for the SettleTrigger), `dishId`/`dishName`/`quantity`,
  `status` defaulting to `QUEUED`, and an `@ElementCollection` of topping snapshots.
- `KitchenTicketItemToppingSnapshot` (`kitchen_ticket_item_toppings`) — `@Embeddable` clone of
  `OrderLineToppingSnapshot`'s identity columns, price column dropped.
- `KitchenProcessedEventEntity` (`kitchen_processed_events`) — idempotency ledger with a unique
  `(event_id, consumer_name)` constraint, physically distinct from the order/inventory ledger tables.

### Task 3: Repositories
- `KitchenTicketRepository` — `existsByOrderId` / `findByOrderId`.
- `KitchenTicketItemRepository` — `lockByOrderIdAndItemId` (`@Lock(PESSIMISTIC_WRITE)`, keyed by
  BOTH `orderId` and item `id`, closing the double-settle-trigger race and the IDOR threat in one
  query) and `findByStatusNot` for the future board read.
- `KitchenProcessedEventRepository` — `existsByEventIdAndConsumerName`.

## Verification

- `./mvnw -o test-compile` succeeded after each task (green build, no warnings/errors).
- `grep -R "PESSIMISTIC_WRITE" src/main/java/com/example/feat1/DDD/kitchen_context` → found in
  `KitchenTicketItemRepository`.
- `grep -R "kitchen_processed_events" src/main/java/com/example/feat1/DDD/kitchen_context` → found in
  `KitchenProcessedEventEntity`, confirming a distinct ledger table.

## Deviations from Plan

None - plan executed exactly as written. (A formatter/linter auto-reformatted long javadoc/annotation
lines across the created files after each Write; this is a whitespace/wrapping-only change, not a
content deviation — verified by successful compiles after each such reformat.)

## Threat Flags

None — both threats in the plan's threat model (T-17-03 IDOR, T-17-04 concurrent transition race)
are mitigated exactly as specified by `lockByOrderIdAndItemId`'s dual-key `PESSIMISTIC_WRITE` query;
no new unaddressed surface was introduced.

## Self-Check: PASSED

- FOUND: src/main/java/com/example/feat1/DDD/kitchen_context/domain/model/KitchenItemStatus.java
- FOUND: src/main/java/com/example/feat1/DDD/kitchen_context/domain/model/KitchenDomainException.java
- FOUND: src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/entity/KitchenTicketEntity.java
- FOUND: src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/entity/KitchenTicketItemEntity.java
- FOUND: src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/entity/KitchenTicketItemToppingSnapshot.java
- FOUND: src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/entity/KitchenProcessedEventEntity.java
- FOUND: src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/repository/KitchenTicketRepository.java
- FOUND: src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/repository/KitchenTicketItemRepository.java
- FOUND: src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/repository/KitchenProcessedEventRepository.java
- FOUND: commit 4afa1cb (Task 1)
- FOUND: commit 8748697 (Task 2)
- FOUND: commit 4fa020a (Task 3)
