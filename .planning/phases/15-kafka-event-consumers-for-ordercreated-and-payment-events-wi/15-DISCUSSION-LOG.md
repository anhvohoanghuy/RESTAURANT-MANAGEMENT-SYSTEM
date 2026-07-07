# Phase 15: kafka-event-consumers - Discussion Log

> **Audit trail only.** Decisions captured in 15-CONTEXT.md — this log preserves the discussion.

**Date:** 2026-07-07
**Phase:** 15-kafka-event-consumers
**Mode:** discuss (interactive)

## Round 1 — initial fire-and-forget design

| Area | Chosen |
|------|--------|
| Deduction trigger | OrderCreated (`orders.created`) |
| Insufficient stock | Allow negative + alert |
| Idempotency | Processed-events ledger (unique `eventId`) |
| Error handling | DefaultErrorHandler + Dead Letter Topic |

## Round 2 — user redesign to an order-confirmation saga

The user revised the approach: order should be created in **`PENDING_CONFIRMATION`**, publish a Kafka event, and only become successful **after Inventory verifies availability**. Additional constraint: **stock must never go negative**.

Follow-up decisions:

| Question | Chosen |
|----------|--------|
| Insufficient stock outcome | Order → **REJECTED** (terminal) |
| When to deduct on success | Only when the item is sent to the kitchen and the kitchen marks it "đang làm" (preparing) |
| Keep non-negative across confirm→kitchen | **Reserve on confirm, deduct for real when kitchen prepares** (`available = on_hand − reserved`) |
| Where does the kitchen "đang làm" status live | **Split into a new Phase 16** |

## Net effect on the design

- **D-02 reversed:** no allow-negative; stock is a hard non-negative invariant.
- **Phase 15 reshaped** from fire-and-forget inventory deduction into a two-context **order-confirmation saga**: `PENDING_CONFIRMATION` → Inventory availability check + **reservation** → result event → `CONFIRMED`/`REJECTED`.
- **Deduction moved out** of Phase 15. Reservations are only *created/held* here.
- **Phase 16 created:** kitchen "đang làm" status + event → convert reservation into actual `on_hand` deduction.
- **Payments consumer dropped** from Phase 15 scope (payment is no longer the deduction trigger).

## Retained from Round 1
- Processed-events ledger (`eventId`) idempotency — now applied to both saga consumers.
- DefaultErrorHandler + Dead Letter Topic error handling.
- Consumer infra mirroring existing producer configs.

## Deferred Ideas
- Reservation release on refund / order cancel (no cancel flow exists).
- `payments.events` consumer.
- Multi-location stock; supplier reorder automation; consumer scaling tuning.
