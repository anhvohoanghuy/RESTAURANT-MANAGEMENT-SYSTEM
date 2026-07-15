---
phase: 20
slug: complete-admin-ui-edit-forms-menu-tables-inventory-recipe-au
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-15
---

# Phase 20 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Frontend phase (`admin-ui/`, Vue 3 + Vite + TypeScript).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | vitest 4.x |
| **Config file** | `admin-ui/` (Vite config; existing `*.test.ts` colocated in `src/`) |
| **Quick run command** | `npm --prefix admin-ui run test` |
| **Full suite command** | `npm --prefix admin-ui run build && npm --prefix admin-ui run test` |
| **Estimated runtime** | ~5–10 seconds |

Baseline: 17/17 tests green pre-implementation (3 test files).

---

## Sampling Rate

- **After every task commit:** Run `npm --prefix admin-ui run test`
- **After every plan wave:** Run `npm --prefix admin-ui run build && npm --prefix admin-ui run test` (build runs `vue-tsc` typecheck)
- **Before `/gsd:verify-work`:** Full suite must be green + build clean
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

Filled by the planner. Guidance — the highest-value automated checks for this phase:

| Area | Wave | Secure/Correct Behavior | Test Type | Automated Command |
|------|------|-------------------------|-----------|-------------------|
| Role decode from JWT | 1 | `decodeRoles(token)` returns `["ADMIN"]` for an ADMIN token, `[]` for malformed | unit | `npm --prefix admin-ui run test` |
| Role-gated render | 1 | ADMIN-only control hidden (`v-if`) when role=STAFF; visible when role=ADMIN; inventory stays visible to STAFF | unit (component) | `npm --prefix admin-ui run test` |
| Shared create+edit modal | 2 | edit opens pre-filled; submit calls the correct PUT with full body (incl. `RecipeRequest.Line` dual `ingredientId`+`ingredient`) | unit (api/module) | `npm --prefix admin-ui run test` |
| Recipe row add/remove | 2 | adding/removing ingredient rows updates model; payload includes both id + display name | unit | `npm --prefix admin-ui run test` |
| Reservation status selector | 2 | emits chosen status (CONFIRMED/NO_SHOW/COMPLETED/CANCELLED), not hardcoded CANCELLED | unit | `npm --prefix admin-ui run test` |
| Session management | 2 | list renders; revoke calls DELETE with sessionId; revoke-others posts | unit (api/module) | `npm --prefix admin-ui run test` |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements — vitest is installed and configured (17 tests already run). No new framework install needed; no new npm packages (role decode is a hand-rolled `atob`).*

---

## Manual-Only Verifications

| Behavior | Why Manual | Test Instructions |
|----------|------------|-------------------|
| End-to-end edit/recipe/session flows against a live Spring Boot backend | No live backend in CI sandbox | Follow `admin-ui/README.md` smoke path: start backend, log in as ADMIN then STAFF, exercise edit modals, recipe builder, session revoke, reservation status; confirm role-gated controls hidden for STAFF |

---

## Validation Sign-Off

- [ ] All tasks have automated verify or are covered by the manual smoke path
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (none needed)
- [ ] No watch-mode flags (use `vitest run`, not watch)
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
