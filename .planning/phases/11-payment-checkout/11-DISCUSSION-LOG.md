# Phase 11: payment-checkout - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md; this log preserves the alternatives considered.

**Date:** 2026-07-05
**Phase:** 11-payment-checkout
**Areas discussed:** phase boundary, payment mode, authorization, roles, partial payments, refunds, Kafka, methods, APIs, order summary, QR placeholders, currency, idempotency, history, DDD context, events

---

## Phase Boundary

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 11 | Create a new phase after Order Submission and keep Phase 10 unchanged. | yes |
| Informal only | Discuss in chat without GSD artifacts. | |
| Extend Phase 10 | Add payment into the completed order submission phase. | |

**User's choice:** Phase 11.
**Notes:** Payment/checkout becomes a new phase after submitted orders exist.

---

## Payment Mode

| Option | Description | Selected |
|--------|-------------|----------|
| Manual payment record | Staff/admin manually records payments. | yes |
| Online gateway | Integrate a real payment provider immediately. | |
| Provider abstraction first | Create provider interfaces and fake provider before real integration. | |

**User's choice:** Manual payment record.
**Notes:** A QR/provider request placeholder is still required for future self-payment.

---

## Authorization And Roles

| Option | Description | Selected |
|--------|-------------|----------|
| Admin/staff only | Staff/admin confirm payments and refunds. | yes |
| Order owner can pay | User can confirm or pay their own order. | |
| Both | User initiates and staff/admin adjusts. | partial |

**User's choice:** Staff/admin confirm payments and refunds; user gets only a QR request placeholder flow.
**Notes:** User requested adding `STAFF` role now instead of only using `ADMIN`.

---

## Partial Payment

| Option | Description | Selected |
|--------|-------------|----------|
| Full payment only | One payment must cover the full order total. | |
| Partial payment | Multiple payments can pay down the submitted order. | yes |
| Split by method | One checkout may include multiple methods in one request. | |

**User's choice:** Partial payment.
**Notes:** Track paid amount, remaining amount, and payment status.

---

## Refunds

| Option | Description | Selected |
|--------|-------------|----------|
| No refund yet | Defer all refunds. | |
| Manual refund record | Staff/admin records refunds manually. | yes |
| Void pending only | Only cancel pending requests. | |

**User's choice:** Manual refund record.
**Notes:** Create a separate refund table/model. Refund attaches to a specific payment record.

---

## Kafka

| Option | Description | Selected |
|--------|-------------|----------|
| Publish events directly after commit | Same pattern as Phase 10; no consumers. | yes |
| Outbox pattern now | More durable but wider scope. | |
| No Kafka | Payment DB only in this phase. | |

**User's choice:** Publish events directly after commit.
**Notes:** Kafka failure should not decide payment success; DB is source of truth.

---

## Payment Methods

| Option | Description | Selected |
|--------|-------------|----------|
| Cash + Bank Transfer + QR Placeholder | `CASH`, `BANK_TRANSFER`, `QR_CODE`. | yes |
| Cash + Card + Bank + QR | Adds manual card/POS. | |
| Broad configurable enum | Includes future provider names. | |

**User's choice:** `CASH`, `BANK_TRANSFER`, `QR_CODE`.
**Notes:** QR code is a placeholder for future provider URL/redirect flow.

---

## APIs

| Option | Description | Selected |
|--------|-------------|----------|
| Order-scoped APIs | Payment APIs hang off submitted orders. | yes |
| Payment resource APIs | `/payments` is primary. | |
| Hybrid | Create by order, read by payment id. | |

**User's choice:** Order-scoped APIs.
**Notes:** Also add admin/staff global payment list with cursor pagination.

---

## Order Payment Summary

| Option | Description | Selected |
|--------|-------------|----------|
| Enrich order via Payment Context port | Order response includes summary without owning payment data. | yes |
| Denormalize into orders | Store payment fields on order. | |
| Payment API only | Keep order response unchanged. | |

**User's choice:** Enrich order reads through a Payment Context port.
**Notes:** Order Context remains free of payment persistence fields.

---

## Refund Status

| Option | Description | Selected |
|--------|-------------|----------|
| Net-based status | Refund can make paid order partially paid again. | |
| Paid remains paid + refund status | Payment status and refund status are separate. | yes |
| Refund-only accounting | Keep paid state untouched with minimal status. | |

**User's choice:** Paid remains paid with refund status separately tracked.
**Notes:** Refunds do not automatically change `PAID` back to `PARTIALLY_PAID`.

---

## QR Payment Request Placeholder

| Option | Description | Selected |
|--------|-------------|----------|
| Pending QR request only | Create placeholder request and staff/admin confirms later. | yes |
| Pending request + cancel | Add user cancellation. | |
| Fake auto-confirm | Demo provider confirmation now. | |

**User's choice:** Placeholder only.
**Notes:** API should be shaped for future third-party `paymentUrl`/`redirectUrl`; no provider call, webhook, redirect handling, or auto-confirm in this phase.

---

## Currency And Overpay

| Option | Description | Selected |
|--------|-------------|----------|
| VND only | Use existing BigDecimal money style. | yes |
| Multi-currency | Add currency field now. | |
| Minor units | Store money as integer minor units. | |

**User's choice:** VND only.
**Notes:** Do not allow overpayment; refund cannot exceed payment amount.

---

## Idempotency

| Option | Description | Selected |
|--------|-------------|----------|
| Required for payment | Prevent duplicate manual payments on retry. | yes |
| Required for payment and refund | Prevent duplicate payments and refunds. | yes |
| Optional | Helpful metadata but not strict. | |

**User's choice:** Required for both payment and refund.
**Notes:** Same idempotency key for same order/payment should return existing record.

---

## Payment History

| Option | Description | Selected |
|--------|-------------|----------|
| Order-scoped only | History only under each order. | |
| Admin global list | Add global staff/admin listing with filters. | yes |
| Payment detail endpoint | Add standalone detail read. | |

**User's choice:** Add admin/staff global list.
**Notes:** Use cursor pagination, default `createdAt desc`, filters for status, method, orderId, dateFrom, dateTo.

---

## Payment Context Boundary

| Option | Description | Selected |
|--------|-------------|----------|
| Payment Context riêng | Separate bounded context. | yes |
| Order Context | Simpler but heavier Order Context. | |
| Hybrid | Payment service separate but tightly enrich orders. | |

**User's choice:** Separate Payment Context.
**Notes:** Payment Context owns persistence, reads orders through ports, and publishes events.

---

## Event Payloads

| Option | Description | Selected |
|--------|-------------|----------|
| Core events only | `PaymentRecorded`, `PaymentRefunded`, `OrderPaymentCompleted`. | yes |
| Verbose status events | Add request and partial-payment lifecycle events. | |
| Generic event | One `PaymentEvent` with type field. | |

**User's choice:** Core events only.
**Notes:** No Kafka consumers in Phase 11.

---

## the agent's Discretion

- Choose exact stable error code names during planning.
- Choose exact DTO/entity names consistent with existing context style.
- Choose exact route names while preserving order-scoped API shape and role boundaries.

## Deferred Ideas

- Real third-party provider integration.
- Provider redirect confirmation and webhooks.
- Outbox pattern for payment events.
- Kafka consumers.
- Checkout sessions.
- Payment-driven table occupancy, kitchen workflow, accounting export, and settlement reporting.
