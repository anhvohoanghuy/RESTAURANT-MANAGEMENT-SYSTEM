# Phase 11 Summary: Payment Checkout Context

## Completed

- Added a separate Payment Context under `payment_context` that owns payment, refund, and QR payment-request persistence and reads submitted orders through a port.
- Added domain model:
  - `PaymentMethod` (`CASH`, `BANK_TRANSFER`, `QR_CODE`).
  - `PaymentStatus` (`UNPAID`, `PARTIALLY_PAID`, `PAID`).
  - `RefundStatus` (`NONE`, `PARTIALLY_REFUNDED`, `REFUNDED`) kept separate from payment status.
  - `PaymentRequestStatus` (`PENDING`, `CONFIRMED`, `FAILED`, `EXPIRED`, `CANCELLED`).
  - `PaymentDomainException` with stable codes for invalid amount, overpay, payment/order not found, unauthorized owner, missing idempotency key, and refund overflow.
- Added persistence:
  - `payments` table with `uk_payments_order_idempotency` unique constraint (order id + idempotency key).
  - `payment_refunds` table with `uk_payment_refunds_idempotency` unique constraint (payment id + idempotency key), attached to a payment via `payment_id`.
  - QR payment-request persistence with provider-ready `paymentUrl`/`redirectUrl`/`expiresAt` fields.
- Added cross-context ports/adapters:
  - Payment Context reads submitted order data through `OrderPaymentLookupPort` + `OrderPaymentLookupAdapter` (backed by `OrderRepository`).
  - Order Context enriches order reads through `PaymentSummaryPort`, implemented by `PaymentService`; Order Context stores no payment state fields.
- Added application services in `PaymentService`:
  - Staff/admin manual payment recording with idempotency-key replay and no-overpay validation.
  - Staff/admin manual refund recording with idempotency-key replay and no-refund-overflow validation.
  - Order owner QR payment request placeholder creation (`PENDING`, provider-ready URLs, no provider call, no auto-confirm).
  - Order payment summary computation (paid, refunded, remaining, payment status, refund status).
  - Order-scoped payment history and global admin/staff history with cursor pagination (default `createdAt desc`).
- Added APIs (`PaymentController`):
  - `GET /orders/{orderId}/payments` (owner-scoped history, includes nested refunds).
  - `POST /orders/{orderId}/payment-requests/qr` (owner QR placeholder).
  - `POST /admin/orders/{orderId}/payments` (staff/admin record payment).
  - `POST /admin/payments/{paymentId}/refunds` (staff/admin record refund).
  - `GET /admin/payments` (global list, cursor pagination).
- Added authorization: seeded `STAFF` role (`RoleEnum.STAFF`, `RoleSeedInitializer`) and restricted `/admin/orders/**` and `/admin/payments`/`/admin/payments/**` to `ADMIN`/`STAFF` in `SecurityConfig`. Ordinary `USER` is forbidden from staff APIs.
- Added Kafka events: `PaymentEvent` payload with `PaymentRecorded`, `PaymentRefunded`, and `OrderPaymentCompleted` types, a `PaymentEventPublisher` port, `KafkaPaymentEventPublisher` adapter, and `PaymentKafkaProducerConfig`. Events are published after transaction commit via `TransactionSynchronization.afterCommit`.

## Verification

- Added unit coverage (`PaymentServiceTest`): idempotency replay, overpay rejection, partial-payment status with separate refund status, and refund-overflow rejection.
- Added integration coverage (`PaymentCheckoutIntegrationTest`): staff records partial/full payments with order-summary reflection, owner forbidden from staff API, order-scoped history, QR placeholder creation, refund, global admin history, and mocked event publishing.
- Focused Payment Context tests re-run during retroactive verification (Java 17, offline Maven):
  - `PaymentServiceTest`: Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
  - `PaymentCheckoutIntegrationTest`: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
- PLAN frontmatter recorded the original full-suite result: `101 tests, 0 failures, 0 errors`.

## Notes

- No Kafka consumers were introduced (`@KafkaListener` count in `src/main` is 0).
- No real payment provider, redirect confirmation, or webhook handling was introduced; QR requests are placeholders only.
- Gap: the global `GET /admin/payments` list implements cursor pagination and `orderId`/`orderUserId` filtering, but the additional filters specified in CONTEXT D-33 (`status`, `method`, `dateFrom`, `dateTo`) are not implemented. See 11-VERIFICATION.md, criterion 9.
