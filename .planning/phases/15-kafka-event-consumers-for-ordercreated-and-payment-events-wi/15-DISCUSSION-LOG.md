# Phase 15: kafka-event-consumers - Discussion Log

> **Audit trail only.** Decisions captured in 15-CONTEXT.md — this log preserves the discussion.

**Date:** 2026-07-07
**Phase:** 15-kafka-event-consumers
**Mode:** discuss (interactive)
**Areas discussed:** Deduction trigger, Insufficient-stock policy, Idempotency, Error handling

## Questions & Answers

### Deduction trigger
- **Options:** OrderCreated (order-submission time) / OrderPaymentCompleted (payment time) / Both-configurable
- **Chosen:** **OrderCreated** (topic `orders.created`)
- **Note:** Resolves the deduction-timing decision deferred from Phase 14; matches when a kitchen actually consumes ingredients.

### Insufficient / negative stock policy
- **Options:** Allow negative + alert / Clamp to 0 + record shortage / Skip deduction + record exception
- **Chosen:** **Allow negative + alert** — record movement, permit negative balance, raise staff alert; never block the already-placed order.

### Idempotency
- **Options:** Processed-events ledger (unique eventId) / Movement reference uniqueness / Consumer offset only
- **Chosen:** **Processed-events ledger** keyed by `eventId`.

### Error handling
- **Options:** DefaultErrorHandler + Dead Letter Topic / Retry then log-and-skip / Manual ack + blocking retry
- **Chosen:** **DefaultErrorHandler + Dead Letter Topic** (`<topic>.DLT`).

## Claude's Discretion (derived, not asked)
- D-05 consumer infrastructure mirroring existing producer configs.
- D-06 deduction mechanism (dish + topping recipes → ingredients → shared UnitConverter → per-ingredient movement; skip-and-alert on missing links).
- D-07 payments.events consumer built on shared infra with minimal/deferred stock action.

## Deferred Ideas
- Stock return on refund / order cancel (no cancel flow exists).
- Payment-triggered deduction option / dual-trigger config.
- Multi-location stock; supplier reorder automation; consumer scaling tuning.
