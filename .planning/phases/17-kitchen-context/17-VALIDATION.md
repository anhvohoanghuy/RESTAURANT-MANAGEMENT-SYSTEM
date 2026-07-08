---
phase: 17
slug: kitchen-context
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-07-08
---

# Phase 17 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + AssertJ (Boot 4 modular `webmvc-test`/`security-test` starters) |
| **Config file** | `src/test/resources/application.properties` — H2 in-memory MySQL-mode, `ddl-auto=create-drop`, `spring.kafka.listener.auto-startup=false` |
| **Quick run command** | `./mvnw test -Dtest=<ClassName>` |
| **Full suite command** | `./mvnw test` (156 tests green as of Phase 16) |
| **Estimated runtime** | Quick ~seconds; full suite ~minutes |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest=<TargetClass>`
- **After every plan wave:** Run `./mvnw test` (full suite)
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** targeted class in seconds; full suite on wave merge

---

## Per-Task Verification Map

| Decision | Behavior | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|----------|----------|------------|-----------------|-----------|-------------------|-------------|--------|
| D-01 | `OrderConfirmed` published exactly once per CONFIRMED transition, after commit | — | N/A | unit | `./mvnw test -Dtest=OrderConfirmationServiceTest` | ❌ W0 (extend) | ⬜ pending |
| D-01 | Kitchen consumer creates exactly one `KitchenTicket` per order (idempotent replay) | T-17 idempotency | Replay does not create duplicate tickets | unit | `./mvnw test -Dtest=KitchenTicketCreationServiceTest` | ❌ W0 | ⬜ pending |
| D-02 | Item forward-only transitions; illegal skip/revert rejected | — | Illegal transition → stable error, no state change | unit | `./mvnw test -Dtest=KitchenTicketAdvanceServiceTest` | ❌ W0 | ⬜ pending |
| D-03 | `SettleTriggerEvent` published once on `QUEUED→PREPARING`, matches Phase 16 shape | T-17 double-settle | Concurrent double-advance cannot double-publish | unit | `./mvnw test -Dtest=KitchenTicketAdvanceServiceTest` | ❌ W0 | ⬜ pending |
| D-04 | `OrderStatus` extended; order-side consumer derives status forward-only, idempotent | T-17 backward-transition | Status never moves backward on replay/out-of-order | unit | `./mvnw test -Dtest=TicketStatusChangedListenerTest` | ❌ W0 | ⬜ pending |
| D-05 | `PATCH /admin/orders/{orderId}/items/{itemId}/status` — ADMIN/STAFF only, illegal transition → stable error | T-17 RBAC | Non-staff → 403; illegal transition → stable error | integration | `./mvnw test -Dtest=KitchenIntegrationTest` | ❌ W0 | ⬜ pending |
| D-05 | Kitchen-board `GET` — lists non-completed items, ADMIN/STAFF only | T-17 RBAC | Non-staff → 403 | integration | `./mvnw test -Dtest=KitchenIntegrationTest` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

**No separate Wave 0.** Each PLAN.md embeds its test file(s) in the same task that implements the
behavior (verified by plan-checker), so there are no dangling MISSING references. The test classes
below are created inline by their owning plans rather than a distinct Wave 0 pass:

- [x] `KitchenTicketCreationServiceTest.java` — D-01 (consumer side) → plan 17-03
- [x] `KitchenTicketAdvanceServiceTest.java` — D-02 / D-03 → plan 17-05
- [x] Extended `OrderConfirmationServiceTest.java` (add, do not replace) — D-01 publish side → plan 17-01
- [x] Order-side status-derivation / `TicketStatusChangedListener` test — D-04 → plan 17-07
- [x] `KitchenIntegrationTest.java` (`@SpringBootTest` + MockMvc, mirrors `InventoryStockIntegrationTest`) — D-05 → plan 17-06
- [x] Broker-free wiring tests mirroring `SettleTriggerKafkaConsumerConfigTest.java` → plans 17-03 / 17-04
- Framework install: **none** — JUnit 5, Mockito, AssertJ, H2, MockMvc, Spring Security test support already present and used by 15+ existing test classes.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live end-to-end Kafka round-trip across contexts (order→kitchen→inventory) | Full saga | No embedded/Testcontainers Kafka in this repo; listeners run with `auto-startup=false` in tests | Optional local run with a real broker: confirm an order, advance an item to PREPARING, observe stock deduction |

*Automated coverage exercises each producer/consumer in isolation (broker-free wiring tests + service unit tests) exactly as Phases 15/16 did.*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify (embedded per-task; plan-checker confirmed no MISSING refs)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (N/A — tests embedded per-task, no separate Wave 0)
- [x] No watch-mode flags
- [x] Feedback latency acceptable (targeted class in seconds)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-07-08 (plan-checker: 0 blockers; Nyquist Dimension 8 passes on actual plan content)
