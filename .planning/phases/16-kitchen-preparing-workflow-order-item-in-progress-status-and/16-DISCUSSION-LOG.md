# Phase 16: Kitchen preparing workflow — Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions captured in 16-CONTEXT.md — this log preserves the discussion.

**Date:** 2026-07-07
**Phase:** 16-kitchen-preparing-workflow-order-item-in-progress-status-and
**Mode:** discuss
**Areas discussed:** Preparing granularity, Trigger, Settlement edge cases, Consumer robustness, Per-item settle mechanics, Order status

## Questions & Selections

### Preparing granularity
- Options: Whole order (order-level) / **Per order-item (line-level)**
- **Selected:** Per order-item — matches phase name "order item in-progress status".

### Trigger
- Options: **Staff API endpoint, role-gated, CONFIRMED→PREPARING only** / Any authed user / Auto on confirmation
- **Selected:** Staff API endpoint, role-gated, from CONFIRMED only.

### Settlement edge cases / non-negative
- Options: **Idempotent + clamp≥0 + DLT on anomaly** / Strict fail-DLT / Best-effort silent clamp
- **Selected:** Idempotent + clamp-to-zero + DLT on missing reservation.

### Consumer robustness
- Options: **Apply WR-01/WR-02 fixes in new consumer** / Mirror Phase 15 exactly
- **Selected:** Apply the Phase 15 review fixes (REQUIRES_NEW idempotency + record movement) in the new settlement consumer. Retro-fix to Phase 15 consumers is separate.

### Per-item settle mechanics (follow-up on per-item choice)
- Options: **Re-resolve recipe per line at prepare time** / Add per-line breakdown to reservation (change Phase 15) / Hybrid display-only
- **Selected:** Re-resolve the recipe per OrderLine at prepare time, reusing the Phase 15 resolution path; decrement on_hand + reserved per line; reservation SETTLED when last line done. No Phase 15 model change. Recipe-change-between-confirm-and-prepare tolerated + logged.

### Order-level status
- Options: **Order → PREPARING when first item starts** / Line-only, order stays CONFIRMED / PREPARING + COMPLETED
- **Selected:** Add OrderStatus.PREPARING; order transitions CONFIRMED→PREPARING when first line starts. No COMPLETED/SERVED (deferred).

## Deferred Ideas
- Order lifecycle beyond preparing (READY/SERVED/COMPLETED).
- Reservation release on cancel/refund.
- Retro-applying WR-01/WR-02 to Phase 15 consumers; remaining WR-03/WR-04/WR-05 review items.
- Payment/Table producer Jackson-3 migration.
- Multi-location stock, reorder automation, concurrency tuning.

## Claude's Discretion (noted)
- Endpoint verb/shape, event/topic names, group-id, retry/backoff, DLT naming, line-status representation, ReservationStatus additions, movement type value.
