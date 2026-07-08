# Phase 17: Kitchen context - Discussion Log

> **Audit trail only.** Not consumed by downstream agents (researcher, planner, executor).
> Decisions are captured in `17-CONTEXT.md`.

**Date:** 2026-07-08
**Phase:** 17-kitchen-context
**Mode:** discuss (default)
**Areas discussed:** Inbound trigger, Item lifecycle, Order status reflection, Staff API surface

## Questions & Selections

### Inbound trigger
- **Q:** How should kitchen learn about a confirmed order and get the line manifest to build ticket items?
- **Options:** New `OrderConfirmed` event with lines · Thin event + lookup port · Reuse `OrderStockResultEvent`
- **Selected:** **New `OrderConfirmed` event carrying the full line manifest** (kitchen builds tickets
  self-contained, no runtime cross-context call). → D-01
- **Note:** order_context today reaches CONFIRMED internally in `OrderConfirmationService.onStockResult()`
  and publishes nothing — this phase adds the outbound event.

### Item lifecycle & settle firing
- **Q:** What lifecycle states/transition rules should an item have, and when does the settle-trigger fire?
- **Options:** QUEUED start, forward-only · Start at preparing · Forward + allow revert
- **Selected:** **QUEUED start, strictly forward one-step, settle-trigger once on QUEUED→preparing.** → D-02, D-03

### Order status reflection
- **Q:** How should fulfillment reflect back onto order status?
- **Options:** Full lifecycle mirror · PREPARING milestone only · PREPARING + COMPLETED only
- **Selected:** **Full lifecycle mirror** — add `PREPARING/READY/SERVED/COMPLETED`; kitchen publishes a
  ticket-status-changed event, order_context consumes and derives order-level status. → D-04

### Staff API surface
- **Q:** What should the staff API under `/admin/orders/**` include?
- **Options:** Advance + kitchen board read · Advance only · Advance + per-order ticket read
- **Selected:** **Advance (write) endpoint + kitchen-board read endpoint.** → D-05

## Claude's Discretion (delegated)
- Event/topic/group-id/DLT names (settle-trigger topic must match `kitchen.settlement-trigger`).
- `KitchenTicket`/item entity shape, DTO shapes, exact URL paths, error codes, board filter/sort.
- Consumer wiring details (reuse Phase 15 Kafka style + Jackson-3 serde).

## Deferred
- Kitchen-board UI, multi-station routing, prep-time analytics.
- Item status revert; reservation release on cancel/refund; Phase 15 WR-03/04/05 follow-ups.
