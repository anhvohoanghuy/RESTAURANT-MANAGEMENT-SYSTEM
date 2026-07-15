# Phase 20: Complete admin UI - Discussion Log

> **Audit trail only.** Not consumed by planning/research/execution agents. Decisions live in 20-CONTEXT.md.

**Date:** 2026-07-15
**Phase:** 20-complete-admin-ui-edit-forms-menu-tables-inventory-recipe-au
**Mode:** discuss
**Areas offered:** Backend-gap scope · Edit-form UX · Recipe/topping depth · Role gating
**Areas selected by user:** Edit-form UX

## Discussion

### Edit-form UX
- **Q: Edit form pattern for menu/table/inventory?** Options: shared create+edit modal / inline-edit in DataTable / dedicated edit page.
  - **User chose:** Shared create+edit modal (reuse Modal.vue). → D-01
- **Q: Do complex forms (recipe, topping — nested repeating rows) also use the modal?** Options: modal for all incl. recipe / modal for simple forms + dedicated screen for recipe.
  - **User chose:** Modal for simple forms; dedicated screen for recipe (and topping). → D-02

## Unselected areas — Claude's defaults (recorded in CONTEXT Claude's Discretion)
- Backend-gap scope → frontend-only; defer missing list endpoints to backlog (matches phase description).
- Recipe/topping depth → full recipe builder + first-class topping management.
- Role gating → decode role from JWT claims (researcher to verify claim), hide ADMIN-only controls for STAFF.

## Deferred ideas
- Backend list endpoints (orders/reservations/admin category-dish listing) — own backend phase/backlog.
- Payment filters — backlog 999.1. Role affordances — folded from backlog 999.2 into this phase.
