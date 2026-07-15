---
phase: 19-vuejs-admin-management-interface
plan: 03
subsystem: testing
tags: [vitest, vue-router, admin-ui, documentation]

# Dependency graph
requires:
  - phase: 19-01
    provides: Vue 3 + Vite + TypeScript scaffold, auth store/login flow, protected router, shared fetch wrapper with 401 refresh retry
  - phase: 19-02
    provides: Typed API wrappers and six fully wired module pages (Menu, Tables, Inventory, Payments, Kitchen, Orders)
provides:
  - Focused unit test coverage for auth session storage (setSession/clearSession/restoreSession/isAuthenticated), API error handling (ApiError, non-JSON bodies, 204 handling, 401-refresh-retry, refresh-failure session clear, retryOnUnauthorized skip), and router auth guards (redirect-to-login, redirect-query preservation, authenticated-blocks-login)
  - admin-ui/README.md documenting local setup, VITE_API_BASE_URL, backend startup expectations, verification commands, and a copy-paste manual smoke path with one write action per module
  - 19-VERIFICATION.md recording build/test pass results and a consolidated backend-gap follow-up table
affects: [future admin-ui polish passes, backend follow-up work closing the documented gaps]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "fetchMock.mockReset() in beforeEach for vitest tests asserting toHaveBeenCalledTimes — a shared module-level vi.fn() otherwise accumulates call history across test cases"

key-files:
  created:
    - admin-ui/src/stores/auth.test.ts
    - .planning/phases/19-vuejs-admin-management-interface/19-VERIFICATION.md
  modified:
    - admin-ui/src/api/client.test.ts
    - admin-ui/src/router/index.test.ts
    - admin-ui/README.md

key-decisions:
  - "Added fetchMock.mockReset() to client.test.ts's beforeEach (Rule 1 bug fix) — the shared, module-scoped vi.fn() carried call history and queued mock responses across test cases, causing new toHaveBeenCalledTimes assertions to fail with cascading call counts."
  - "Overview module metric placeholders ('-' values with an explicit in-page gap notice) were verified as an intentional, already-documented gap from 19-01/19-02 rather than a new stub introduced by this plan; recorded in 19-VERIFICATION.md's backend-gap table rather than fixed."

requirements-completed: [ADMIN-UI-005, ADMIN-UI-006]

# Metrics
duration: ~20min
completed: 2026-07-15
---

# Phase 19 Plan 03: Verification Coverage & Operating Documentation Summary

**17 vitest unit tests across auth storage, API client (including 401-refresh-retry), and router guards, plus a complete admin-ui/README.md operating guide and a phase-level 19-VERIFICATION.md recording green build/test results and five documented backend gaps.**

## Performance

- **Duration:** ~20 min
- **Completed:** 2026-07-15
- **Tasks:** 3 (as planned)
- **Files modified/created:** 5 (2 created, 3 modified)

## Accomplishments

- Closed the D-06 "focused tests for API client/auth guard where practical" requirement: added `src/stores/auth.test.ts` (6 tests) and extended `src/api/client.test.ts` (7 tests, up from 2) and `src/router/index.test.ts` (4 tests, up from 2) — 17 tests total across 3 files, all green.
- Discovered and fixed a real test-isolation bug in the pre-existing `client.test.ts`: the module-scoped `fetchMock` was never reset between tests, so newly added `toHaveBeenCalledTimes` assertions failed with call counts inflated by prior tests' queued mocks.
- Rewrote `admin-ui/README.md` from a minimal setup note into a full operating guide: requirements, env var table, backend/dependency startup expectations, verification commands, a numbered manual smoke path exercising one write action per module, and the known-gap registry.
- Authored `.planning/phases/19-vuejs-admin-management-interface/19-VERIFICATION.md` recording final `npm run build`/`npm run test` results, a per-test-file breakdown, and a consolidated backend-gap follow-up table cross-referencing existing `.planning/STATE.md` deferred items (backlog 999.1) and new gaps (menu/reservation/order listing, overview aggregates).

## Task Commits

Each task was committed atomically:

1. **Task 1: Add focused frontend tests** - `6987228` (test)
2. **Task 2: Document local operation** - `48f1575` (docs)
3. **Task 3: Record verification** - `6bc6e12` (docs)

**Plan metadata:** (this commit, docs: complete plan)

## Files Created/Modified

- `admin-ui/src/stores/auth.test.ts` - New: `setSession`/`clearSession`/`restoreSession`/`isAuthenticated` coverage including localStorage persistence and corrupt-JSON recovery
- `admin-ui/src/api/client.test.ts` - Extended: non-JSON error fallback, 204 handling, 401→refresh→retry-once success, refresh-failure session clear, `retryOnUnauthorized: false` skip; added `fetchMock.mockReset()` to `beforeEach`
- `admin-ui/src/router/index.test.ts` - Extended: redirect-query preservation, authenticated-user-hits-`/login`-redirects-to-`overview`
- `admin-ui/README.md` - Rewritten: requirements, env vars, backend expectations, verification commands, manual smoke path, known-gap registry
- `.planning/phases/19-vuejs-admin-management-interface/19-VERIFICATION.md` - New: build/test results, test breakdown, environment notes, manual verification status, backend-gap table, success-criteria checklist

## Decisions Made

- Fixed the `fetchMock` reset-between-tests gap immediately (Rule 1 — the existing tests happened not to trip over it because they only used `toHaveBeenCalledWith`, but the newly added tests needed exact call counts to prove the refresh-retry logic doesn't loop or double-fire).
- Kept the Overview module's `-` metric placeholders as-is; verified they are an already-documented, intentional gap (in-page notice explaining "Overview aggregate endpoints are not available yet") from 19-01/19-02, not a new stub — recorded in the verification doc's gap table instead of treating it as this plan's problem to fix.
- `npm install` was required before either `npm run build` or `npm run test` would run — `node_modules` was absent in this worktree despite `package-lock.json` being present (expected, since `node_modules` is gitignored and this worktree started clean); installed via `npm install`, no lockfile changes.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Missing `fetchMock` reset caused inflated call counts across tests**
- **Found during:** Task 1, first `npm run test` run after adding the new `client.test.ts` cases
- **Issue:** `client.test.ts` defines `fetchMock` once at module scope and calls `vi.stubGlobal('fetch', fetchMock)` in `beforeEach`, but never reset the mock's call history or queued `mockResolvedValueOnce` implementations. The pre-existing 2 tests never noticed because they only used `toHaveBeenCalledWith` (existence check, not count). The 5 new tests added exact `toHaveBeenCalledTimes` assertions, which failed with counts like 7, 9, and 10 instead of the expected 1-3, because earlier tests' queued mock responses and call history bled into later tests.
- **Fix:** Added `fetchMock.mockReset()` as the first line of `beforeEach`.
- **Files modified:** `admin-ui/src/api/client.test.ts`
- **Verification:** `npm run test` — 3 files, 17 tests, all pass, deterministically, across repeated runs.
- **Committed in:** `6987228` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 bug). No scope creep — the fix was required for the new tests (this plan's own deliverable) to pass reliably.

## Issues Encountered

- `node_modules/` was absent at the start of this plan's execution (fresh worktree checkout; `node_modules` is gitignored). Ran `npm install` (210 packages, 0 vulnerabilities) before any build/test command would run. No `package-lock.json` changes.
- No running Spring Boot backend, Postgres, Redis, or Kafka broker in this execution environment, so the manual smoke path documented in `admin-ui/README.md` could not be executed end-to-end here. `npm run build` and `npm run test` — the two automated checks this plan's `<verification>` section requires — do not need the backend and both passed. This is recorded explicitly in `19-VERIFICATION.md` as a follow-up for whoever runs the app against a live backend.

## Known Stubs

- `admin-ui/src/views/OverviewView.vue` renders hardcoded `-` values for all four metric cards (open table sessions, low-stock ingredients, kitchen queue, payments today), with an explicit in-page notice stating "Overview aggregate endpoints are not available yet." This is a pre-existing, intentional gap from 19-01/19-02 (no backend summary endpoint exists), not new work from this plan. It is documented in `19-VERIFICATION.md`'s backend-gap table as a v1.1 follow-up item, matching the D-05 "disabled or documented, never faked" principle from `19-CONTEXT.md`. Not fixed here — resolving it requires a new backend aggregate endpoint, out of Phase 19's scope (D-04).

## User Setup Required

None - no external service configuration required. To exercise the manual smoke path in
`admin-ui/README.md` against live data, point `VITE_API_BASE_URL` (default `http://localhost:8080`)
at a running instance of this repo's Spring Boot backend (with Postgres/Redis/Kafka up) and sign in
with an ADMIN/STAFF account.

## Next Phase Readiness

- Phase 19 (VueJS admin management interface) is now feature-complete across all 3 plans: 19-01 (scaffold/auth/router/shell), 19-02 (six module views wired to live API), 19-03 (test coverage + operating docs + verification record).
- `npm run build` and `npm run test` both pass at this plan's final commit; no known frontend defects.
- Five backend gaps are documented (menu/dish listing, reservation listing, payment filters (existing backlog 999.1), global order listing, overview aggregates) — none block Phase 19 sign-off per D-04/D-05, but are candidates for future backend-side v1.1/v1.2 work.
- No blockers identified for phase close or milestone wrap-up.

---
*Phase: 19-vuejs-admin-management-interface*
*Completed: 2026-07-15*
