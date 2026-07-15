---
phase: 20-complete-admin-ui-edit-forms-menu-tables-inventory-recipe-au
plan: 01
subsystem: api
tags: [vue, typescript, jwt, api-bindings, admin-ui]

# Dependency graph
requires:
  - phase: 19-vue-admin-ui
    provides: modules.ts CRUD bindings + auth.ts session lifecycle (setSession/restoreSession/clearSession) this plan extends
provides:
  - "Typed update* PUT bindings for category, dish, area, table, ingredient in modules.ts"
  - "upsertRecipe + recipeCost + retyped getRecipe (RecipeResponse) in menuApi"
  - "sessionsApi (list/revoke/revokeOthers) reading authState.refreshToken live"
  - "Recipe*/RecipeCost*/AuthSessionResponse types mirroring backend Java records"
  - "authState.roles + isAdmin computed decoded from the JWT access token"
affects: [20-02, 20-03, 20-04, 20-05, 20-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Hand-rolled atob-based JWT payload decode (no jwt-decode library) — payload only, no signature verification (server already verified it)"
    - "sessionsApi.revokeOthers reads authState.refreshToken live at call time, not a captured value, to avoid revoking the calling device"

key-files:
  created:
    - admin-ui/src/api/modules.test.ts
  modified:
    - admin-ui/src/api/modules.ts
    - admin-ui/src/stores/auth.ts
    - admin-ui/src/stores/auth.test.ts

key-decisions:
  - "recipeCost placed in menuApi (per PLAN.md task action), not grouped with updateIngredient in inventoryApi as the RESEARCH.md code-example grouping suggested — the plan's explicit task instruction is authoritative"
  - "RecipeLine type declares both ingredientId and ingredient (display name) fields per the verified backend RecipeRequest.Line record — avoids the Pitfall 2 trap from RESEARCH.md"

patterns-established:
  - "New modules.ts sections follow the existing `// --- Section ---` banner convention; sessionsApi added as its own 'Auth sessions' banner section"

requirements-completed: [API-BINDINGS, ROLE-GATE, SESSIONS, D-01, D-02]

# Metrics
duration: 3min
completed: 2026-07-15
---

# Phase 20 Plan 01: Shared API + Auth Foundation Summary

**Typed update*/recipe/session API bindings added to modules.ts and JWT role decoding (`authState.roles` + `isAdmin`) added to auth.ts, unblocking all four Wave 2 view plans to build against a stable contract.**

## Performance

- **Duration:** 3 min
- **Started:** 2026-07-15T17:38:15+07:00 (first task commit)
- **Completed:** 2026-07-15T17:39:58+07:00 (last task commit)
- **Tasks:** 3 completed
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments
- `modules.ts` gained 7 new typed bindings (`updateCategory`, `updateDish`, `updateArea`, `updateTable`, `updateIngredient`, `upsertRecipe`, `recipeCost`) plus a retyped `getRecipe` (was `apiFetch<unknown>`, now `apiFetch<RecipeResponse>`)
- New `sessionsApi` module (`list`/`revoke`/`revokeOthers`) with `revokeOthers` correctly reading the live `authState.refreshToken` (mitigates T-2001-02 self-DoS threat from the plan's threat model)
- `auth.ts` gained `decodeRoles()` (hand-rolled atob JWT payload decode, try/catch-safe) and `isAdmin` computed, wired into both `setSession()` and `restoreSession()` lifecycle points additively (no signature change to `AuthSession`/`setSession`/`clearSession`)
- Full test coverage: `auth.test.ts` extended with `decodeRoles`/`isAdmin` describe blocks (ADMIN, STAFF, malformed-token, no-roles-claim, restoreSession-recompute cases); new `modules.test.ts` covers `sessionsApi.revokeOthers`/`revoke` and a representative `update*` PUT-binding shape
- `npm --prefix admin-ui run build` (vue-tsc typecheck) and `npm --prefix admin-ui run test` both green: 26/26 tests across 4 test files (17 pre-existing + 6 new auth + 3 new modules)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add all new typed bindings + types to modules.ts** - `9986f9a` (feat)
2. **Task 2: Add JWT role decode + isAdmin to auth.ts and test it** - `b14b9fb` (test, RED) → `08591c9` (feat, GREEN)
3. **Task 3: Add modules.test.ts covering sessionsApi + a PUT binding shape** - `e7a9bc0` (test)

**Plan metadata:** (this commit, see final commit below)

_Note: Task 2 is a TDD task — RED (`b14b9fb`, tests fail because `decodeRoles`/`isAdmin` are not yet exported) then GREEN (`08591c9`, implementation added, all tests pass). No REFACTOR commit was needed._

## Files Created/Modified
- `admin-ui/src/api/modules.ts` - Added Recipe*/RecipeCost*/AuthSessionResponse types, 7 update*/recipe bindings, sessionsApi module
- `admin-ui/src/stores/auth.ts` - Added `decodeRoles()`, `authState.roles`, `isAdmin` computed; wired into setSession/restoreSession/clearSession
- `admin-ui/src/stores/auth.test.ts` - Added `decodeRoles`/`isAdmin` describe blocks (6 new tests)
- `admin-ui/src/api/modules.test.ts` - New file: sessionsApi + menuApi.updateDish request-shape coverage (3 new tests)

## Decisions Made
- Placed `recipeCost` in `menuApi` (matching PLAN.md's task action text) rather than `inventoryApi` (where RESEARCH.md's "Code Examples" section had grouped it alongside `updateIngredient` for narrative convenience) — the plan's explicit instruction takes precedence over the research doc's illustrative grouping.
- `npm install` was run once at the start of execution because `admin-ui/node_modules` did not exist in this worktree (fresh checkout). This restored already-declared dependencies from the committed `package.json`/`package-lock.json` — no new packages were added, so this is a lockfile-restore, not a new dependency install requiring package-legitimacy verification.

## Deviations from Plan

None - plan executed exactly as written. All acceptance criteria and the `<verify>` commands for all three tasks passed on first attempt with no auto-fixes required.

## Issues Encountered
- `admin-ui/node_modules` was absent in the freshly-created worktree (expected — `node_modules` is gitignored). Ran `npm install` (Bash tool, not a Rule 3 exclusion since it restores the lockfile rather than installing an unverified package) before running build/test verification commands.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `modules.ts` and `auth.ts` are now the stable, fully-typed, unit-tested contract that Wave 2 view plans (20-02 Menu/Recipe, 20-03 Tables, 20-04 Inventory, and any Sessions view plan) build against without needing to re-touch either file.
- `isAdmin` is available for `v-if` gating and router `meta: { adminOnly: true }` guards per D-02/Pitfall 3 in RESEARCH.md.
- No blockers for Wave 2.

---
*Phase: 20-complete-admin-ui-edit-forms-menu-tables-inventory-recipe-au*
*Completed: 2026-07-15*

## Self-Check: PASSED

All created/modified files verified present on disk; all commit hashes (`9986f9a`, `b14b9fb`, `08591c9`, `e7a9bc0`, `3c3a5a6`) verified present in `git log`.
