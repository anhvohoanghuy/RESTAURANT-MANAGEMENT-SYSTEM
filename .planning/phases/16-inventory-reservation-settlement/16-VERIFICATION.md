---
phase: 16-inventory-reservation-settlement
verified: 2026-07-08T03:46:24Z
status: passed
score: 9/9 must-haves verified
has_blocking_gaps: false
overrides_applied: 0
re_verification:
  # initial verification — no prior VERIFICATION.md existed
---

# Phase 16: Inventory reservation settlement — Verification Report

**Phase Goal:** Add an Inventory settlement consumer that converts a held reservation into an actual stock deduction (`reserved` → `on_hand` decreases, never negative) on a settle-trigger event carrying `(orderId, orderLineId, totalLines)`. Inventory re-resolves each line's recipe (reuse Phase 15 path), deducts under a pessimistic lock with a non-negative clamp, records a CONSUMPTION audit movement, marks the reservation `SETTLED` on the last line, and is idempotent (eventId ledger + per-`(orderId, orderLineId)` guard, WR-01 `REQUIRES_NEW`) with DLT on a missing reservation. Pure inventory concern — does NOT create the trigger and does NOT touch order status.

**Verified:** 2026-07-08T03:46:24Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #  | Truth | Status | Evidence |
| -- | ----- | ------ | -------- |
| 1  | Thin `SettleTriggerListener` @KafkaListener delegates 100% to `InventoryReservationSettlementService.onSettleTrigger` | ✓ VERIFIED | `SettleTriggerListener.java:26-28` — body is a single `settlementService.onSettleTrigger(event)` call, zero business logic; `containerFactory = "settleTriggerKafkaListenerContainerFactory"`, topic/group from locked property keys |
| 2  | Settlement re-resolves the ONE line's recipe via shared `RecipeRequirementResolver` + `OrderLineLookupPort`; locks reservation BEFORE balance rows (ascending ingredientId); decrements BOTH on_hand and reserved; clamps on_hand ≥ 0 without throwing | ✓ VERIFIED | `InventoryReservationSettlementService.java`: `findLine` (108-113) + `resolveLineRequirements` via `recipeRequirementResolver.resolveForTarget/accumulate` (208-219); `lockByOrderId` (118) precedes `lockByIngredientAndLocation` (138); ingredients sorted ascending (134); subtract on both `newReserved`/`newOnHand` (151-152), applied at 168-169; on_hand clamp to ZERO with log, no throw (160-166) |
| 3  | WR-02: a CONSUMPTION `InventoryStockMovementEntity` is written DIRECTLY per settlement; `InventoryStockService.recordMovement` is NOT called in the settlement path | ✓ VERIFIED | Direct `new InventoryStockMovementEntity()` + `movementRepository.save` per ingredient (176-189), `movementType=CONSUMPTION`, `referenceType="ORDER_LINE"`, `referenceId=orderLineId`, `baseQuantityDelta=need.negate()`; grep for `recordMovement` in service returns nothing |
| 4  | WR-01: idempotency-ledger insert isolated in `InventoryLedgerWriter` @Transactional(REQUIRES_NEW) — not the Phase-15 saveAndFlush+catch inline idiom | ✓ VERIFIED | `InventoryLedgerWriter.java:39-40` `@Transactional(propagation = Propagation.REQUIRES_NEW) boolean tryInsert(...)`; distinct `@Component` so proxy takes effect; service calls `ledgerWriter.tryInsert` (101). Returns false on `DataIntegrityViolationException` without propagating |
| 5  | Idempotency: eventId processed-events ledger AND per-(orderId, orderLineId) `InventoryLineSettlementEntity` guard both present; replay is a no-op | ✓ VERIFIED | Pre-check `existsByEventIdAndConsumerName(...) OR existsByOrderIdAndOrderLineId(...)` → return (88-96); named unique constraint `uq_inventory_line_settlement (order_id, order_line_id)` on `InventoryLineSettlementEntity`; tests `duplicateEventIdSkips`, `duplicateOrderLineSkipsEvenWithNewEventId` |
| 6  | Reservation → SETTLED only when last distinct line settles (`countByOrderId >= totalLines` under reservation lock) | ✓ VERIFIED | `settledCount = countByOrderId(orderId); if (settledCount >= event.totalLines()) reservation.setStatus(SETTLED)` (202-205), dirty-checked while holding the reservation lock; tests `marksSettledOnlyWhenLastLineSettles`, `outOfOrderLastLineStillFlipsExactlyOnce` |
| 7  | Missing HELD reservation / missing order line → throws (routes retry → DLT; not in any non-retryable set) | ✓ VERIFIED | `findLine(...).orElseThrow(settlementOrderLineMissing)` (112), `lockByOrderId(...).orElseThrow(settlementReservationMissing)` (121); non-HELD reservation is benign early-return (122-127). Config marks ONLY `DeserializationException` non-retryable; tests `missingReservationThrowsForDltRouting`, `alreadySettledReservationIsBenign` |
| 8  | Kafka wiring mirrors Phase 15: ErrorHandlingDeserializer → Jackson-3 JacksonJsonDeserializer (trusted packages), AckMode.RECORD, DefaultErrorHandler + DeadLetterPublishingRecoverer, NewTopic beans (settle + DLT); no new deps; no legacy Jackson-2 | ✓ VERIFIED | `SettleTriggerKafkaConsumerConfig.java`: EHD wrapping `JacksonJsonDeserializer` (57-58), `TRUSTED_PACKAGES` + `VALUE_DEFAULT_TYPE` + `USE_TYPE_INFO_HEADERS=false` (60-64), `AckMode.RECORD` (93), `DefaultErrorHandler`+`DeadLetterPublishingRecoverer`+`FixedBackOff(1000,3)` (72-74); `InventoryKafkaTopicConfig` `settleTriggerTopic()` + `settleTriggerDltTopic()` from same @Value + `DLT_SUFFIX` (55-63); pom.xml unchanged (no diff since phase start); no `org.springframework.kafka...JsonDeserializer` legacy import anywhere in src |
| 9  | SCOPE: no order status, no order-item status, no staff HTTP endpoint, no order-aggregate mutation added | ✓ VERIFIED | Phase-16 commit range (`3ebb0dc^..865b2ba`) changed only inventory_context files + two read-only order_context files (`OrderLineRepository` = single derived read query; `OrderLineLookupAdapter` = `@Transactional(readOnly=true)`). `OrderStatus.java` / `OrderEntity.java` NOT in the range. Only `setStatus` in the settlement path is `reservation.setStatus(SETTLED)` on the inventory reservation aggregate |

**Score:** 9/9 truths verified

### CONTEXT.md Decisions D-01..D-06

| Decision | Status | Evidence |
| -------- | ------ | -------- |
| D-01 (thin inbound event `(eventId, eventType, occurredAt, orderId, orderLineId, totalLines)`, no ingredient amounts, Jackson-3 serde, trusted packages, no new deps) | ✓ HONORED | `SettleTriggerEvent` record has exactly those 6 components + `TYPE="SettleTrigger"`, no BigDecimal/ingredient field; serde round-trip test present (6 references) |
| D-02 (re-resolve single line via the SAME extracted resolver; no duplicated resolution) | ✓ HONORED | `RecipeRequirementResolver` is the single resolver; `InventoryReservationService` refactored to delegate (`accumulate` at 169/173, `accumulateRecipe` removed); settlement uses same resolver |
| D-03 (non-negative clamp: clamp on_hand to 0, never negative, never throw) | ✓ HONORED | Clamp at 160-166, no throw; test `clampsOnHandToZeroAndLogsAnomaly`; reserved also clamped as safeguard (153-159) |
| D-04 (SETTLED only on last line via per-(orderId,orderLineId) count vs totalLines) | ✓ HONORED | count-then-flip under reservation lock (202-205); `ReservationStatus = {HELD, SETTLED}` |
| D-05 (eventId ledger + per-line guard; missing HELD reservation → DLT) | ✓ HONORED | Two guards (88-96), missing reservation throws (121) → retryable → DLT |
| D-06 (WR-01 REQUIRES_NEW ledger writer; WR-02 CONSUMPTION movement) | ✓ HONORED | `InventoryLedgerWriter` REQUIRES_NEW; direct CONSUMPTION movement, `recordMovement` avoided |

No deviations from D-01..D-06 detected.

### Required Artifacts

| Artifact | Status | Details |
| -------- | ------ | ------- |
| `SettleTriggerEvent.java` | ✓ VERIFIED | Record with exact 6-field contract, no recipe data |
| `InventoryMovementType.java` | ✓ VERIFIED | `CONSUMPTION` declared + classified `isOutbound()` |
| `StockReservationEntity.java` | ✓ VERIFIED | `ReservationStatus = {HELD, SETTLED}`; no derivation from lines |
| `InventoryLineSettlementEntity.java` + repository | ✓ VERIFIED | Named unique `(order_id, order_line_id)`; `existsByOrderIdAndOrderLineId` + `countByOrderId` |
| `StockReservationRepository.lockByOrderId` | ✓ VERIFIED | `@Lock(PESSIMISTIC_WRITE)` + JPQL |
| `OrderLineLookupPort` / `OrderLineRecipeSnapshot` / `OrderLineLookupAdapter` / `OrderLineRepository` | ✓ VERIFIED | Narrow snapshot; adapter read-only, keyed by both ids via `findByOrder_IdAndId` |
| `RecipeRequirementResolver.java` | ✓ VERIFIED | Shared resolver injecting only `MenuRecipeCostingPort` + `IngredientRepository` |
| `InventoryLedgerWriter.java` | ✓ VERIFIED | REQUIRES_NEW isolated insert |
| `InventoryReservationSettlementService.java` | ✓ VERIFIED | Full settlement sequence (1)-(8) as specified |
| `SettleTriggerKafkaConsumerConfig.java` | ✓ VERIFIED | Typed factory + error handler + container factory |
| `InventoryKafkaTopicConfig.java` | ✓ VERIFIED | settle + DLT NewTopic beans from one property |
| `SettleTriggerListener.java` | ✓ VERIFIED | Thin delegate |

### Key Link Verification

| From | To | Status | Details |
| ---- | -- | ------ | ------- |
| `SettleTriggerListener` | `settlementService.onSettleTrigger` | ✓ WIRED | Direct delegate call |
| `onSettleTrigger` | `ledgerWriter.tryInsert` | ✓ WIRED | REQUIRES_NEW insert (101) |
| `onSettleTrigger` | `reservationRepository.lockByOrderId` | ✓ WIRED | Acquired before balance locks (118) |
| `onSettleTrigger` | `RecipeRequirementResolver.accumulate/resolveForTarget` | ✓ WIRED | Single-line re-resolution (211-215) |
| `onSettleTrigger` | `InventoryMovementType.CONSUMPTION` | ✓ WIRED | Direct movement write (179) |
| `InventoryReservationService.computeRequired` | `RecipeRequirementResolver.accumulate` | ✓ WIRED | Phase-15 path delegates (169/173) |
| `SettleTriggerKafkaConsumerConfig` | `DeadLetterPublishingRecoverer` | ✓ WIRED | Over reused `inventoryDltKafkaTemplate` |
| `InventoryKafkaTopicConfig` | settle topic + `.DLT` | ✓ WIRED | Both from `DLT_SUFFIX` |

### Behavioral Spot-Checks

Suite reported green (156/0/0) by the launching agent; not re-run per instruction. Test coverage confirmed by enumeration:

| Behavior | Evidence | Status |
| -------- | -------- | ------ |
| Deduction + clamp + movement + SETTLED-on-last + idempotency + missing-reservation throw + benign-already-settled + lock-order | `InventoryReservationSettlementServiceTest` — 9 behavior tests + `locksReservationBeforeBalances` all present | ✓ PASS (exist) |
| WR-01 REQUIRES_NEW contract | `InventoryLedgerWriterTest` present | ✓ PASS (exist) |
| Kafka hardening broker-free | `SettleTriggerKafkaConsumerConfigTest` present | ✓ PASS (exist) |
| Jackson-3 serde round-trip | `EventSerdeRoundTripTest` (SettleTriggerEvent case) | ✓ PASS (exist) |
| Cross-context read mapping | `OrderLineLookupAdapterTest` present | ✓ PASS (exist) |

### Requirements Coverage

No formal REQ IDs — phase driven by CONTEXT decisions D-01..D-06 (all HONORED, table above). Plan `requirements` fields reference D-01..D-06; all satisfied.

### Anti-Patterns Found

None material. No TODO/FIXME/XXX debt markers in the phase source. Clamp/no-throw and empty-balance `continue` paths are intentional per D-03. pom.xml unmodified — no supply-chain change.

### Human Verification Required

None in scope. The settle-trigger producer is the Phase 17 kitchen context; per 16-CONTEXT.md the consumer intentionally has no live upstream and is exercised via unit/slice tests until Phase 17. Live end-to-end broker validation is deferred to Phase 17 by design, so no in-scope behavior is left unverifiable here.

### Gaps Summary

No gaps. All 9 observable truths verified against actual source (not SUMMARY claims), all six CONTEXT decisions honored, scope boundary respected (no order-status/endpoint/aggregate mutation leaked in from the deferred Phase 17 concerns). The settlement consumer is correct, idempotent (two independent guards + REQUIRES_NEW isolation), non-negative (clamped, never throws on underflow), auditable (direct CONSUMPTION movement), and DLT-routed on missing data.

---

_Verified: 2026-07-08T03:46:24Z_
_Verifier: Claude (gsd-verifier)_
