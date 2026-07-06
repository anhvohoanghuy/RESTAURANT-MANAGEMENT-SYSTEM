# Phase 14 Discussion Log: Inventory Management

**Date:** 2026-07-06

## User Prompt

- User requested implementing Inventory Costing and preparing the next warehouse/inventory management phase.

## Decisions Captured

- Phase 14 follows Phase 13 and continues the Inventory Context.
- Phase 14 focuses on stock-on-hand and stock movement management.
- Use a single default stock location first.
- Keep automatic order deduction out of this phase unless explicitly re-scoped after discussion.
- Keep purchase orders and suppliers deferred.

## Open Questions Before Execution

- Should automatic stock deduction happen on order submission, payment completion, or kitchen fulfillment?
- Should stock count corrections allow negative-to-zero reconciliation without strict outbound validation?
- Which movement reasons should be mandatory for waste and adjustment flows?
- Should low-stock threshold be per ingredient only, or per ingredient plus future location?
