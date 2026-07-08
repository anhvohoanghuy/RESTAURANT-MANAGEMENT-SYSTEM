---
phase: 17
slug: kitchen-context
status: draft
nyquist_compliant: false
wave_0_complete: false
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

- [ ] `KitchenTicketCreationServiceTest.java` — covers D-01 (consumer side)
- [ ] `KitchenTicketAdvanceServiceTest.java` — covers D-02 / D-03
- [ ] Extend `OrderConfirmationServiceTest.java` (add, do not replace) — covers D-01 publish side
- [ ] `TicketStatusChangedListenerTest.java` / order-side derivation service test — covers D-04
- [ ] `KitchenIntegrationTest.java` (`@SpringBootTest` + MockMvc, mirrors `InventoryStockIntegrationTest`) — covers D-05
- [ ] Broker-free wiring tests mirroring `SettleTriggerKafkaConsumerConfigTest.java`: `KitchenKafkaProducerConfigTest`, `OrderConfirmedKafkaConsumerConfigTest`, `TicketStatusChangedKafkaConsumerConfigTest`
- Framework install: **none** — JUnit 5, Mockito, AssertJ, H2, MockMvc, Spring Security test support already present and used by 15+ existing test classes.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live end-to-end Kafka round-trip across contexts (order→kitchen→inventory) | Full saga | No embedded/Testcontainers Kafka in this repo; listeners run with `auto-startup=false` in tests | Optional local run with a real broker: confirm an order, advance an item to PREPARING, observe stock deduction |

*Automated coverage exercises each producer/consumer in isolation (broker-free wiring tests + service unit tests) exactly as Phases 15/16 did.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency acceptable (targeted class in seconds)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
