# Phase 13 Discussion Log: Inventory Costing

**Date:** 2026-07-06

## User Prompt

- User requested: "Inventory costing."
- User then requested GSD planning for Inventory costing.

## Decisions Captured

- Treat Inventory Costing as a new phase after completed Phase 12.
- Bring prior out-of-scope "Inventory costing" into roadmap scope.
- Keep this phase backend-only.
- Build a costing foundation, not full stock control.
- Create a separate Inventory Context for ingredient and cost ownership.
- Keep recipes in Menu Context, adding optional ingredient links while preserving existing ingredient text behavior.
- Do not change public menu response, order totals, payment totals, or stock levels in this phase.

## Open Questions For Implementation

- Whether cost records should later support supplier-specific pricing.
- Whether future costing should be FIFO, weighted-average, or latest-cost for inventory valuation.
- Whether stock deduction should happen on order submission, payment completion, or kitchen fulfillment in a later phase.
