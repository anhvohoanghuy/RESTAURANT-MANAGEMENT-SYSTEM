---
phase: 16
slug: kitchen-preparing-workflow-order-item-in-progress-status-and
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
| **Estimated runtime** | ~60–90 seconds (full suite; 138 tests as of Phase 15) |

---

## Sampling Rate

- **After every task commit:** Run the focused `./mvnw -q -Dtest=<TestClass> test` for that task's test.
- **After every plan wave:** Run `./mvnw -q test` (full suite) — catches cross-plan integration (context load, schema).
- **Before `/gsd:verify-work`:** Full suite must be green.
- **Max feedback latency:** ~90 seconds.

---

## Per-Task Verification Map

Task IDs are filled in by the planner; this maps the phase's must-prove behaviors to test types. All
tests are broker-free (settlement logic and Kafka wiring are unit/slice-tested, as in Phase 15).

| Behavior to prove | Wave | Threat Ref | Secure/Correct Behavior | Test Type | Automated Command |
|-------------------|------|------------|-------------------------|-----------|-------------------|
| `OrderStatus.PREPARING` added; order CONFIRMED→PREPARING on first line prepared | 1 | — | Only a CONFIRMED order can transition; first line flips order to PREPARING | unit | `./mvnw -q -Dtest=OrderPreparingServiceTest test` |
| Staff endpoint role-gated (ADMIN/STAFF only), CONFIRMED-only precondition | 3 | T-16 auth | 403 for non-staff; 409/422 when order not CONFIRMED | slice (MockMvc) | `./mvnw -q -Dtest=*PreparingControllerTest test` |
| Per-line settlement re-resolves recipe and decrements on_hand + reserved by that line | 2 | — | on_hand and reserved decrease by the line's per-ingredient base qty; never negative (clamp≥0) | unit | `./mvnw -q -Dtest=InventorySettlementServiceTest test` |
| Reservation → SETTLED only when last line settled | 2 | — | Partial settle keeps HELD; final line flips to SETTLED | unit | `./mvnw -q -Dtest=InventorySettlementServiceTest test` |
| Idempotency: replayed preparing event / re-settled (orderId,lineId) is a no-op | 2 | T-16 idempotency | Duplicate delivery does not double-deduct (ledger + per-line guard) | unit | `./mvnw -q -Dtest=InventorySettlementServiceTest test` |
| WR-01: idempotency insert in REQUIRES_NEW — no UnexpectedRollbackException on concurrent duplicate | 2 | T-16 idempotency | Duplicate-key collision does not poison the business transaction | unit | `./mvnw -q -Dtest=InventorySettlementServiceTest test` |
| WR-02: an InventoryStockMovement (consumption) is recorded per settlement | 2 | — | Movement row exists for audit of the actual deduction | unit | `./mvnw -q -Dtest=InventorySettlementServiceTest test` |
| Missing HELD reservation routes to DLT (not swallowed) | 2/3 | T-16 poison | Anomalous record → DLT; DefaultErrorHandler + DeadLetterPublishingRecoverer | slice | `./mvnw -q -Dtest=*KafkaConsumerConfigTest test` |
| Preparing event serde round-trips (Jackson-3 native, carries totalLines) | 3 | — | Serialize/deserialize equal; trusted packages `com.example.feat1.*` | unit | `./mvnw -q -Dtest=*SerdeRoundTripTest test` |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- Existing infrastructure covers all phase requirements — JUnit 5 + Spring Boot Test + H2 already configured
  and used through Phase 15 (138 tests green). No new framework install needed.

---

## Manual-Only Verifications

| Behavior | Why Manual | Test Instructions |
|----------|-----------|-------------------|
| End-to-end settlement over a real Kafka broker | Suite is deliberately broker-free (mirrors Phase 15) | Run `docker compose up -d kafka`, submit → confirm → mark an item preparing via the staff endpoint, assert `on_hand`/`reserved` decreased and a movement row was written |

*All automated-testable behaviors have automated verification above; the live-broker round-trip is the only manual check (same posture as Phase 15).*

---

## Validation Sign-Off

- [ ] All tasks have an automated verify or an existing-infra test mapping
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (none — existing infra)
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
