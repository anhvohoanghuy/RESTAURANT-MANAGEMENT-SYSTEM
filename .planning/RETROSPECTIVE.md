# Project Retrospective

A living record of what was learned each milestone.

## Milestone: v1.0 — MVP

**Shipped:** 2026-07-15
**Phases:** 19 | **Plans:** 50 | **Tasks:** 76 | **Commits:** 311 | **LOC:** ~25,700 Java

### What Was Built

A DDD Spring Boot 4 / Kafka restaurant-management backend across eight bounded contexts: auth/identity (local + Google, JWT, hardening), menu (catalog + validation/quoting), table (catalog + operations), order (cart → submission → confirmation saga → cancellation), payment (checkout, refunds, auto-refund), inventory (costing + management + reservation/settlement/release), kitchen (per-item fulfillment lifecycle), and a shared transactional outbox. All cross-context effects flow through idempotent, at-least-once Kafka events with DefaultErrorHandler + DLT.

### What Worked

- **Bounded-context isolation paid off at parallelization time.** Phase 18's three Wave-2 plans (order / inventory / payment) touched disjoint packages, so they ran concurrently in isolated worktrees and merged with zero conflicts.
- **Goal-backward verification + code review as separate gates.** The verifier confirmed must-haves; the code reviewer caught three cross-file interaction bugs the verifier's coarser checks missed (projection NPE on a partially-cancelled item, over-broad whole-order refund, movement-type misclassification). Both gates were needed.
- **Idempotency ledgers + outbox everywhere** meant the "duplicate publish" class of concerns stayed bounded to amplification, never double business-effect.

### What Was Inefficient

- **Requirements traceability drifted.** Phase 14's INV-012..021 shipped and verified but stayed unchecked in REQUIREMENTS.md until milestone close. Checkboxes should be updated at each phase close, not retroactively.
- **PROJECT.md never existed during the milestone**, so the per-phase "evolve PROJECT.md" step was silently skipped throughout. Created at close instead.
- **Re-scoping churn around Phases 15–17.** Phase 16 was re-scoped mid-flight (deduction split into settlement + a new kitchen context), and Phase 17 spawned two inserted hardening phases (17.1, 17.2) to absorb review debt. Correct outcomes, but the review debt could have been smaller with tighter first-pass plans.

### Patterns Established

- Transactional outbox (`outbox_events` + per-row relay + retention) as the durable publish path for all saga events.
- REQUIRES_NEW ledger-writer beans for idempotency inserts isolated from the business transaction.
- Cross-context reads via narrow snapshot ports (e.g. `KitchenItemStatusPort`, `OrderLineLookupPort`) instead of shared aggregates.
- Compensation-on-cancel: release inventory reservation + auto event-driven refund + kitchen void, all guarded by the "before kitchen starts" window.

### Key Lessons

- Run the code-review gate before declaring a phase done — a passing verifier is necessary but not sufficient for cross-file correctness.
- Keep planning docs (REQUIREMENTS.md checkboxes, PROJECT.md) in sync at each phase close; drift is cheap to prevent and tedious to reconcile.
- Single-instance outbox topology is fine for v1.0; a durable multi-instance claim (intermediate CLAIMING status) is the known upgrade path if the relay is ever scaled out.

## Cross-Milestone Trends

| Milestone | Phases | Plans | Commits | Notable |
| --------- | ------ | ----- | ------- | ------- |
| v1.0 MVP  | 19     | 50    | 311     | 8 bounded contexts; event-driven saga + compensation; 257 tests green |
