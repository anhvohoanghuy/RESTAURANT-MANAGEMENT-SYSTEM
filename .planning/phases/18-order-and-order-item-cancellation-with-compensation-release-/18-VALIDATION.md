---
phase: 18
slug: order-and-order-item-cancellation-with-compensation-release-
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-10
---

# Phase 18 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Spring Boot Test) + Mockito, H2 in-memory (MODE=MySQL) for slice/integration |
| **Config file** | `src/test/resources/application.properties` (H2; outbox relay + retention gated off) |
| **Quick run command** | `./mvnw -q -Dtest=<TestClass> test` |
| **Full suite command** | `./mvnw test` |
| **Estimated runtime** | ~90–150 seconds (full suite, currently 213 tests) |

---

## Sampling Rate

- **After every task commit:** Run the focused `-Dtest=<TestClass>` for the task's class
- **After every plan wave:** Run `./mvnw test` (full suite)
- **Before `/gsd:verify-work`:** Full suite must be green (0 failures, 0 errors)
- **Max feedback latency:** ~150 seconds (full suite)

---

## Per-Task Verification Map

> Filled by the planner as PLAN.md tasks are created; each cancel/compensation task
> maps to a focused test class. Kafka-consumer paths (inventory release, payment refund,
> kitchen void) are exercised broker-free via slice/unit tests, consistent with the
> existing outbox/consumer test convention (relay disabled under the test profile).

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | TBD | TBD | Cancel window guard | T-18-01 | Cancel rejected once PREPARING+ | unit/slice | `./mvnw -q -Dtest=OrderCancellationServiceTest test` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Test classes for the new cancellation service(s) and each new consumer (inventory
      release, payment refund, kitchen void) — stubs mapped to phase requirement IDs.

*Existing Maven/JUnit infrastructure covers framework needs — no framework install required.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| End-to-end refund on a real broker | Auto Payment refund | Full Kafka broker not run in CI; consumers are unit/slice-tested | Deploy locally with Kafka, cancel a paid order, confirm refund recorded |

---

## Validation Sign-Off

- [ ] All tasks have automated verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 150s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
