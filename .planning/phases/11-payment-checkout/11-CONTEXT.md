# Phase 11: payment-checkout - Context

**Gathered:** 2026-07-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Add a backend Payment Context for checkout after submitted orders exist. This phase covers manual staff/admin payment recording, partial payments, manual refunds, QR/provider payment request placeholders for future self-payment, order payment-summary enrichment, and payment Kafka events.

In scope:
- A separate Payment Context owns payment, refund, and payment-request persistence.
- Payment Context reads submitted order data through ports and does not move order ownership out of Order Context.
- Manual payments are the MVP; no real payment gateway is integrated.
- Staff/admin can record payments and refunds.
- Authenticated order owners can create QR payment request placeholders for future provider payment URLs.
- Payment/refund events are published after commit for future consumers.

Out of scope:
- Real VNPAY/MoMo/Stripe/provider calls.
- Redirect confirmation and provider webhook handling.
- Kafka consumers.
- Checkout session abstraction.
- Payment-driven kitchen/display/table occupancy workflows.
- Accounting exports and settlement reports beyond basic payment history.

</domain>

<decisions>
## Implementation Decisions

### Phase Shape
- **D-01:** Payment/checkout is Phase 11, separate from Phase 10 order submission.
- **D-02:** The phase uses a new Payment Context, not Order Context, for payment/refund/payment-request models and services.
- **D-03:** The phase is backend/API-focused; no frontend UI is required.

### Roles And Authorization
- **D-04:** Add a `STAFF` role in this phase.
- **D-05:** `STAFF` and `ADMIN` can record manual payments and refunds.
- **D-06:** Ordinary authenticated users cannot confirm payments or refunds.
- **D-07:** Authenticated order owners can create QR payment request placeholders for their own submitted orders.

### Payment Model
- **D-08:** MVP payment mode is manual payment record, not a real online gateway.
- **D-09:** Supported payment methods are `CASH`, `BANK_TRANSFER`, and `QR_CODE`.
- **D-10:** Currency is VND only.
- **D-11:** Amounts follow the existing `BigDecimal` money pattern and must be positive.
- **D-12:** Support partial payments.
- **D-13:** Payment status values are `UNPAID`, `PARTIALLY_PAID`, and `PAID`.
- **D-14:** Confirmed payments cannot overpay the submitted order total.
- **D-15:** Do not add a separate `/checkout` endpoint; checkout behavior is expressed through payment APIs on submitted orders.

### Idempotency
- **D-16:** Manual payment creation requires an `idempotencyKey`.
- **D-17:** Retrying payment creation with the same idempotency key for the same order returns the existing payment instead of creating a duplicate.
- **D-18:** Refund creation also requires an `idempotencyKey` and follows the same duplicate-prevention rule.

### Refunds
- **D-19:** Phase 11 supports manual refund records.
- **D-20:** Refunds are stored in a dedicated table/model such as `payment_refunds`.
- **D-21:** Refunds attach to a specific payment record through `payment_id`.
- **D-22:** Refund amount cannot exceed the original payment amount.
- **D-23:** Refund status/summary is separate from payment status. A paid order remains payment-status `PAID` even when refunds exist.

### QR Payment Request Placeholder
- **D-24:** QR self-payment is a provider-ready placeholder only.
- **D-25:** The request model should support statuses suitable for later provider work: `PENDING`, `CONFIRMED`, `FAILED`, `EXPIRED`, and `CANCELLED`.
- **D-26:** The API shape may return `paymentUrl` or `redirectUrl` fields that later come from a third-party provider.
- **D-27:** Phase 11 does not call a provider, process redirect confirmation, process webhook confirmation, or auto-confirm QR requests.
- **D-28:** Staff/admin manual confirmation remains the only way to turn money into a confirmed payment record in this phase.

### APIs
- **D-29:** Use order-scoped payment APIs as the primary shape.
- **D-30:** Expected endpoint family includes order-scoped history, staff/admin payment creation, staff/admin refunds, and user QR payment request creation.
- **D-31:** Add an admin/staff global payment list.
- **D-32:** Global payment list uses cursor pagination with default ordering by `createdAt desc`.
- **D-33:** Global payment list supports basic filters: status, method, orderId, dateFrom, and dateTo.

### Order Integration
- **D-34:** Order response should include payment summary.
- **D-35:** Order Context must not store payment state fields.
- **D-36:** Order Context enriches reads by calling a Payment Context port for payment summary.
- **D-37:** If Payment Context has no payment records, the default summary is unpaid: paid amount 0, refunded amount 0, remaining amount equal to order total.

### Events
- **D-38:** Publish payment events directly after transaction commit, mirroring Phase 10.
- **D-39:** Kafka publish failure should not roll back the payment database transaction.
- **D-40:** Phase 11 publishes core events only: `PaymentRecorded`, `PaymentRefunded`, and `OrderPaymentCompleted`.
- **D-41:** Do not add Kafka consumers in this phase.

### the agent's Discretion
- Choose exact DTO names, event field names, stable error codes, and repository method shapes consistent with the existing Auth/Order Context style.
- Decide whether `POST /admin/orders/{orderId}/payments` or a close equivalent is cleaner during planning, as long as order-scoped APIs and staff/admin authorization are preserved.
- Decide whether QR payment request persistence is a separate table or a typed payment-request table, as long as provider-ready URL/status fields exist and no real provider integration is implied.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Planning Scope
- `.planning/ROADMAP.md` - Phase 11 goal, requirements, and success criteria.
- `.planning/REQUIREMENTS.md` - `PAY-001` through `PAY-014` and deferred scope boundaries.
- `.planning/STATE.md` - Current milestone and phase status.
- `.planning/phases/11-payment-checkout/11-DISCUSSION-LOG.md` - Human discussion trail for alternatives considered.

### Direct Dependencies
- `.planning/phases/10-order-submission-mvp/10-CONTEXT.md` - Submitted order boundary and deferred payment scope.
- `.planning/phases/10-order-submission-mvp/10-01-SUMMARY.md` - Current order persistence, APIs, and event publishing behavior.
- `.planning/phases/09-order-cart-mvp/09-CONTEXT.md` - Order Context ownership and cart/order boundary.
- `.planning/phases/06-auth-hardening/06-CONTEXT.md` - Auth/session/audit hardening context relevant to role-protected operations.
- `.planning/phases/02-auth-context-mvp/02-CONTEXT.md` - Role seeding and security route conventions.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `OrderRepository` and submitted order entities in `src/main/java/com/example/feat1/DDD/order_context/` provide the source submitted order data Payment Context must read through an adapter/port.
- `OrderSubmissionService` already publishes after-commit Kafka events; Payment Context should reuse that pattern.
- `SecurityConfig` already protects `/admin/**`, `/orders/**`, and `/users/**`; Phase 11 will extend route protection for `STAFF`.
- `RoleSeedInitializer` seeds current roles and should be extended for `STAFF`.
- Global exception handling already returns `{ code, message, timestamp }`.

### Established Patterns
- Contexts live under `src/main/java/com/example/feat1/DDD/*_context/`.
- Cross-context reads use ports/adapters, as Order Context does for Menu and Table validation.
- Tests prefer focused unit tests plus Spring `MockMvc` integration tests with mocked external ports.
- Kafka event publishing currently has no consumers in this codebase.

### Integration Points
- Payment Context reads submitted order id, owner id, status, and total through an Order Context adapter.
- Order Context reads payment summaries through a Payment Context port when returning order responses.
- Payment Context publishes `PaymentRecorded`, `PaymentRefunded`, and `OrderPaymentCompleted` events.
- Auth/identity context must seed and authorize `STAFF`.

</code_context>

<specifics>
## Specific Ideas

- Use VND-only amounts.
- Use cursor pagination for global admin/staff payment history.
- Keep QR provider payment URL/redirect URL as a placeholder shape for future third-party integration.
- Keep payment status and refund status separate so refunds do not automatically pull a paid order back to partially paid.

</specifics>

<deferred>
## Deferred Ideas

- Real payment provider integration.
- Provider redirect confirmation and webhooks.
- Payment outbox pattern.
- Kafka consumers in other contexts.
- Checkout session abstraction.
- Payment-driven table occupancy, kitchen workflow, accounting export, or settlement reporting.

</deferred>

---

*Phase: 11-payment-checkout*
*Context gathered: 2026-07-05*
