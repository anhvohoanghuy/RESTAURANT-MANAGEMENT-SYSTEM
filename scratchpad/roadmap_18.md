**Plans:** 6 plans

Plans:

**Wave 1**
- [ ] 18-01-PLAN.md — Foundation: append CANCELLED status + terminal guards, cancel error codes, OrderLineEntity.cancelledAt, OrderRepository.lockById, OrderCancelledEvent contract + topic (CANCEL-07)

**Wave 2** *(all depend on 18-01; disjoint contexts, fully parallel)*
- [ ] 18-02-PLAN.md — order_context cancellation core: KitchenItemStatusPort/adapter + OrderCancellationService (window guard, ownership, race-safe kitchen read, partial recompute, outbox publish) (CANCEL-01/02/04)
- [ ] 18-03-PLAN.md — inventory reservation release consumer: inverse-of-settlement service, release ledger/enums, listener + DLT config (CANCEL-05)
- [ ] 18-04-PLAN.md — payment auto-refund consumer: Payment's first ledger + consumer, whole-order-gated refund reusing recordRefund (CANCEL-06)

**Wave 3** *(depend on 18-02)*
- [ ] 18-05-PLAN.md — REST cancel endpoints (customer + admin) + authorization integration test (CANCEL-02/03/04)
- [ ] 18-06-PLAN.md — kitchen ticket void consumer: append CANCELLED status, guarded idempotent void, advance guard, listener + DLT config (CANCEL-08 / D-7)
