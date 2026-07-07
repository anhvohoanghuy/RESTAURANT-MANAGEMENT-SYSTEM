---
phase: 15
slug: kafka-event-consumers
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-07
---

# Phase 15 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test (Mockito, MockMvc), H2 in-memory DB |
| **Config file** | `pom.xml` (surefire); `src/test/resources/application.properties` |
| **Quick run command** | `./mvnw -q -Dtest='Inventory*,Order*' test` |
| **Full suite command** | `./mvnw test` |
| **Estimated runtime** | ~60 seconds (full suite, 119+ tests) |

> Kafka: no live broker in tests. Set `spring.kafka.listener.auto-startup=false` in test properties and invoke the `@Transactional` listener/handler services directly under H2 (per RESEARCH.md). EmbeddedKafka is optional, not required.

---

## Sampling Rate

- **After every task commit:** Run `./mvnw -q -Dtest='Inventory*,Order*' test`
- **After every plan wave:** Run `./mvnw test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~60 seconds

---

## Per-Task Verification Map

> Filled by the planner per task. Each behaviour-adding task must map to an automated JUnit test (unit for reservation math / idempotency / availability; integration for the saga status transition and DLT routing).

| Task ID | Plan | Wave | Requirement | Secure Behavior | Test Type | Automated Command | Status |
|---------|------|------|-------------|-----------------|-----------|-------------------|--------|
| (planner fills) | | | | | | | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Serde round-trip test for `OrderCreatedEvent` (close the untested-serializer gap flagged in RESEARCH.md A2).

*Otherwise: existing JUnit/Spring Boot Test infrastructure covers all phase requirements — no new framework install needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| End-to-end delivery over a real Kafka broker | saga | No broker in CI; tests exercise handlers directly | Run app with a local broker, submit an order, observe PENDING_CONFIRMATION → CONFIRMED/REJECTED |

---

## Validation Sign-Off

- [ ] All tasks have automated verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
