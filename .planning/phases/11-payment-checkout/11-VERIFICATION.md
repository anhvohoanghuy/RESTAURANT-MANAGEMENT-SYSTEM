---
phase: 11-payment-checkout
verified: 2026-07-07T10:22:00Z
status: passed
score: 9/10 success criteria fully verified (1 partial, non-blocking)
has_blocking_gaps: false
---

# Phase 11 Verification: Payment Checkout Context

**Phase Goal:** Add a Payment Context so staff/admin can record manual partial payments and refunds for submitted orders, users can create QR payment request placeholders for future providers, order reads can show payment summaries, and payment events are published for future consumers.

**Verified:** 2026-07-07
**Verification type:** Retroactive (SUMMARY and VERIFICATION were both missing)
**Overall verdict:** PASS (9 PASS, 1 PARTIAL — non-goal-blocking)

## Command

```bash
# Java 17 (openjdk 17.0.19), offline Maven
./mvnw -q -o test -Dtest='PaymentServiceTest,PaymentCheckoutIntegrationTest'
```

## Result

- Status: Passed
- `PaymentServiceTest`: Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
- `PaymentCheckoutIntegrationTest`: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
- Total focused Payment Context tests: 6 passed, 0 failed
- PLAN frontmatter recorded original full-suite run: 101 tests, 0 failures, 0 errors

## Success Criteria Verdicts

| # | Criterion | Verdict | Evidence |
|---|-----------|---------|----------|
| 1 | Payment logic in a separate Payment Context, reads orders through ports | PASS | `src/main/java/com/example/feat1/DDD/payment_context/**` is a self-contained context. `OrderPaymentLookupPort` + `OrderPaymentLookupAdapter` (backed by `OrderRepository`) provide order reads. Order Context has no payment persistence fields; it depends only on `PaymentSummaryPort`. |
| 2 | STAFF and ADMIN can record payments; ordinary users cannot | PASS | `SecurityConfig` lines 61-62 restrict `/admin/orders/**` and `/admin/payments`/`/admin/payments/**` to `hasAnyRole("ADMIN","STAFF")`. Integration test `staffRecordsPaymentsAndOrderSummaryReflectsStatus` asserts owner (USER) POST to `/admin/orders/{id}/payments` returns 403; staff succeeds. |
| 3 | Manual payments support CASH, BANK_TRANSFER, QR_CODE; VND amounts; require idempotency keys | PASS | `PaymentMethod` enum has all three. `PaymentService.recordPayment` calls `requireIdempotencyKey` and `validatePositive` (BigDecimal, `precision=12, scale=2`). Integration test exercises CASH, BANK_TRANSFER, and QR_CODE. |
| 4 | Partial payment accounting with UNPAID, PARTIALLY_PAID, PAID | PASS | `PaymentStatus` enum {UNPAID, PARTIALLY_PAID, PAID}. `summarizeTotals` derives status from paid vs order total. Integration test asserts PARTIALLY_PAID (70000/140000) then PAID (140000/140000, remaining 0). |
| 5 | Refunds in a separate `payment_refunds` model, attach to a payment, require idempotency keys, refund summary separate from payment status | PASS | `PaymentRefundEntity` maps `@Table(name="payment_refunds")` with `@ManyToOne` `payment_id` and `uk_payment_refunds_idempotency`. `recordRefund` requires idempotency key. `RefundStatus` computed independently in `summarizeTotals` (paid order stays PAID even with refunds). Unit test `summarizeUsesPaidAmountForPaymentStatusAndRefundStatusSeparately`. |
| 6 | Confirmed payments cannot overpay order total; refunds cannot exceed payment amount | PASS | `recordPayment` throws `overpayNotAllowed()` when `paid + amount > order total`. `recordRefund` throws `refundExceedsPayment()` when `refunded + amount > payment amount`. Unit tests `recordPaymentRejectsOverpay` and `recordRefundRejectsRefundsBeyondOriginalPaymentAmount`. |
| 7 | Users can create QR payment request placeholders returning a provider payment/redirect URL shape, without real provider integration or auto-confirm | PASS | `createQrPaymentRequest` sets status `PENDING`, populates `paymentUrl`/`redirectUrl`/`expiresAt` from a config base URL; no provider call, no auto-confirm. `PaymentRequestStatus` includes future provider states. Integration test asserts PENDING + `paymentUrl` prefix. |
| 8 | Order read APIs enrich responses with payment summary via a port to Payment Context | PASS | `OrderSubmissionService` (lines 138-154) calls `paymentSummaryPort.summarize(orderId, total)` and returns `SubmittedOrderPaymentSummary`. `PaymentService implements PaymentSummaryPort`. Integration test asserts `$.payment.paymentStatus`, `paidAmount`, `remainingAmount` on `GET /orders/{id}`. |
| 9 | Admin/staff can view order-scoped payment/refund history and global payment history with cursor pagination and basic filters | PARTIAL | Order-scoped history `GET /orders/{orderId}/payments` (owner-scoped, refunds nested) works. Global `GET /admin/payments` implements cursor pagination (`createdAt desc`, `nextCursor`/`hasMore`) and filters by `orderId`/`orderUserId`. **Gap:** the CONTEXT D-33 filters `status`, `method`, `dateFrom`, `dateTo` are NOT implemented in `PaymentController.listPayments` / `PaymentService.listPayments` Specification. ROADMAP wording "basic filters" is minimally met (orderId + pagination). Non-goal-blocking. |
| 10 | Successful operations publish PaymentRecorded, PaymentRefunded, OrderPaymentCompleted after commit, no consumers | PASS | `PaymentEvent` defines all three types. `publishAfterCommit` registers `TransactionSynchronization.afterCommit`. `recordPayment` publishes PAYMENT_RECORDED and ORDER_PAYMENT_COMPLETED (when PAID); `recordRefund` publishes PAYMENT_REFUNDED. `KafkaPaymentEventPublisher` is the only adapter. `@KafkaListener` count in `src/main` = 0. Integration test verifies publisher invoked (mocked, `atLeast(2)`). |

**Score:** 9/10 fully verified; criterion 9 PARTIAL.

## Artifacts Verified

| Artifact | Status |
|----------|--------|
| `payment_context/application/PaymentService.java` | VERIFIED (implements PaymentSummaryPort; all flows present) |
| `payment_context/infrastructure/presentation/PaymentController.java` | VERIFIED (5 endpoints wired) |
| `payment_context/infrastructure/entity/PaymentEntity.java` | VERIFIED (`payments`, uniqueness constraint) |
| `payment_context/infrastructure/entity/PaymentRefundEntity.java` | VERIFIED (`payment_refunds`, FK + uniqueness) |
| `payment_context/infrastructure/entity/PaymentRequestEntity.java` | VERIFIED (QR placeholder persistence) |
| `payment_context/domain/model/*` (PaymentMethod, PaymentStatus, RefundStatus, PaymentRequestStatus, PaymentDomainException) | VERIFIED |
| `payment_context/domain/port/OrderPaymentLookupPort` + adapter | VERIFIED (wired to OrderRepository) |
| `order_context/domain/port/PaymentSummaryPort` + OrderSubmissionService usage | VERIFIED (order reads enriched) |
| `payment_context/application/event/PaymentEvent.java` + KafkaPaymentEventPublisher + PaymentKafkaProducerConfig | VERIFIED |
| `SecurityConfig.java` STAFF/ADMIN route protection | VERIFIED |
| `RoleEnum.STAFF` + `RoleSeedInitializer` seeding | VERIFIED |
| Tests: `PaymentServiceTest`, `PaymentCheckoutIntegrationTest` | VERIFIED (6 tests pass) |

## Gaps

| # | Gap | Severity | Detail |
|---|-----|----------|--------|
| 1 | Global payment list filters incomplete | minor (non-blocking) | `GET /admin/payments` supports cursor pagination and `orderId`/`orderUserId`, but CONTEXT D-33 also specified `status`, `method`, `dateFrom`, and `dateTo` filters. These are not implemented. ROADMAP criterion 9 ("basic filters") is minimally satisfied, so this does not block the phase goal. Recommend backlog: add the four missing filters to `PaymentService.listPayments` Specification and controller `@RequestParam`s. |

## Coverage Notes

- `PaymentServiceTest` covers idempotency replay, overpay rejection, partial-payment vs separate refund status, and refund-overflow rejection.
- `PaymentCheckoutIntegrationTest` covers the authenticated HTTP flow: staff partial/full payment with order-summary reflection, owner-forbidden staff API (403), order-scoped history, QR placeholder, refund, global admin history, and mocked Kafka publishing without a real broker or provider.

---

_Verified: 2026-07-07T10:22:00Z_
_Verifier: Claude (gsd-verifier), retroactive_
