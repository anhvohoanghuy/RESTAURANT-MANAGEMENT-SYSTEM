---
phase: 16
slug: inventory-reservation-settlement
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-07
---

# Phase 16 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test (Spring Boot 4.0.6), Mockito, H2 in-memory |
| **Config file** | `pom.xml` (spring-boot-starter-test), `src/test/resources/application.properties` |
| **Quick run command** | `./mvnw -q -Dtest=<TestClass> test` |
| **Full suite command** | `./mvnw -q test` |
| **Estimated runtime** | ~60–90 seconds (full suite) |

---

## Sampling Rate

- **After every task commit:** Run the focused `./mvnw -q -Dtest=<TestClass> test`.
- **After every plan wave:** Run `./mvnw -q test` (full suite) — catches cross-plan integration (context load, schema).
- **Before `/gsd:verify-work`:** Full suite must be green.
- **Max feedback latency:** ~90 seconds.

---

## Per-Task Verification Map

All tests are broker-free (settlement logic + Kafka wiring are unit/slice-tested, as in Phase 15;
the settle-trigger producer is Phase 17).

| Behavior to prove | Threat Ref | Correct/Secure Behavior | Test Type | Automated Command |
|-------------------|------------|-------------------------|-----------|-------------------|
| Per-line recipe re-resolution matches the Phase 15 path | — | A line's dish+toppings resolve to the same per-ingredient base quantities | unit | `./mvnw -q -Dtest=InventorySettlementServiceTest test` |
| Deduct decrements BOTH quantity_on_hand and reserved_quantity by the line amounts | — | Both columns drop by the resolved per-ingredient qty | unit | `./mvnw -q -Dtest=InventorySettlementServiceTest test` |
| Non-negative clamp when on_hand < reserved | T-16 invariant | on_hand clamped to 0, anomaly logged, never negative | unit | `./mvnw -q -Dtest=InventorySettlementServiceTest test` |
| CONSUMPTION movement recorded per settlement (WR-02) | — | An InventoryStockMovementEntity row exists for the deduction | unit | `./mvnw -q -Dtest=InventorySettlementServiceTest test` |
| Reservation → SETTLED only when last line settles (via totalLines) | — | Partial settle keeps HELD; the totalLines-th distinct line flips SETTLED | unit | `./mvnw -q -Dtest=InventorySettlementServiceTest test` |
| Idempotency: replayed eventId / re-settled (orderId,lineId) is a no-op | T-16 replay | No double-deduction on redelivery (ledger + per-line guard) | unit | `./mvnw -q -Dtest=InventorySettlementServiceTest test` |
| WR-01: ledger insert in REQUIRES_NEW — no UnexpectedRollbackException on concurrent duplicate | T-16 replay | Duplicate-key collision does not poison the business transaction | unit/slice | `./mvnw -q -Dtest=*SettlementLedger*Test test` |
| Lock ordering: reservation row locked before balance rows (deadlock-free) | — | lockByOrderId acquired first, then sorted-id balance locks | unit | `./mvnw -q -Dtest=InventorySettlementServiceTest test` |
| Missing HELD reservation → DLT | T-16 poison | Record routes to DLT; DefaultErrorHandler + DeadLetterPublishingRecoverer | slice | `./mvnw -q -Dtest=*SettlementKafkaConsumerConfigTest test` |
| Settle-trigger event serde round-trips (Jackson-3 native) | — | Serialize/deserialize equal; trusted packages com.example.feat1.* | unit | `./mvnw -q -Dtest=*SerdeRoundTripTest test` |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- Existing infrastructure covers all phase requirements — JUnit 5 + Spring Boot Test + H2 already
  configured and green through Phase 15. No new framework install needed.

---

## Manual-Only Verifications

| Behavior | Why Manual | Test Instructions |
|----------|-----------|-------------------|
| End-to-end settlement over a real Kafka broker | Suite is broker-free; the producer is Phase 17 | After Phase 17, publish a settle-trigger via kitchen and assert on_hand/reserved dropped + a CONSUMPTION movement row was written |

*All automated-testable behaviors have automated verification above; the live end-to-end round-trip is deferred to Phase 17 (producer) + a manual broker check.*

---

## Validation Sign-Off

- [ ] All tasks have an automated verify or existing-infra test mapping
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (none — existing infra)
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
