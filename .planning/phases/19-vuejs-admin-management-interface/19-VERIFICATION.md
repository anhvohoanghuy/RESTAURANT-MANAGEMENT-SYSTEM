# Phase 19: VueJS Admin Management Interface — Verification

**Verified:** 2026-07-15
**Scope:** `admin-ui/` (Vue 3 + Vite + TypeScript), plans 19-01, 19-02, 19-03.

---

## Automated Verification

Run from `admin-ui/`:

```bash
npm install   # only needed if node_modules is absent
npm run build
npm run test
```

| Command | Result | Details |
|---------|--------|---------|
| `npm run build` (`vue-tsc -b && vite build`) | PASS | Typecheck clean; production bundle emitted (`dist/index.html`, `dist/assets/index-*.css` 8.00 kB, `dist/assets/index-*.js` 158.09 kB gzip 51.85 kB). Build completes in ~0.2s. |
| `npm run test` (`vitest run`) | PASS | 3 test files, 17 tests, 0 failures. Duration ~0.8-1.2s. |

### Test file breakdown

| File | Tests | Covers |
|------|-------|--------|
| `src/api/client.test.ts` | 7 | Bearer token attach, `ApiError` with backend code, non-JSON error body fallback, 204 No Content handling, 401→refresh→retry-once success path, refresh failure clears session (no infinite retry), `retryOnUnauthorized: false` skips refresh (used by login). |
| `src/router/index.test.ts` | 4 | Protected route redirects to `/login` when logged out, redirect query param preserves the originally requested path, protected route renders when logged in, authenticated user visiting `/login` is redirected to `overview`. |
| `src/stores/auth.test.ts` | 6 | `setSession` populates state + persists to `localStorage` (including `tokenType` default-to-`Bearer`), `clearSession` resets state + removes persisted session + sets the session-expired message, `restoreSession` rehydrates from a persisted session, no-op when nothing persisted, and recovers (clears storage) from corrupt JSON. |

These three files were the direct output of 19-03 Task 1, added on top of the 19-01 scaffold and 19-02 module views to close the D-06 "focused tests for API client/auth guard where practical" requirement.

### Environment notes

This execution environment has no running Spring Boot backend, Postgres, Redis, or Kafka broker.
`npm run build` and `npm run test` do not require the backend — both were run and passed without
one. Full data-flow verification against a live backend (the manual smoke path below) has **not**
been executed in this environment and remains a task for whoever runs this against a live
`VITE_API_BASE_URL` instance, per `admin-ui/README.md`.

---

## Manual Verification

**Status:** Documented, not executed in this environment (no backend available).

The manual smoke path is recorded in `admin-ui/README.md` under "Manual Smoke Path" and covers:
unauthenticated redirect to `/login`, login as ADMIN/STAFF, visiting all seven sidebar modules
(Overview, Menu, Tables, Inventory, Payments, Kitchen, Orders), one write action per module that
has one, and session-expiry redirect back to `/login`.

Per 19-02's summary, a `vite` dev server was previously started in the 19-02 execution
environment and every route's compiled module returned HTTP 200 with no Vite transform errors —
confirming the app boots and all six module views are syntactically/type-sound, short of live
backend data verification.

---

## Backend Gaps (Follow-up Items)

These are pre-existing backend capability gaps surfaced explicitly in the UI (via `GapNotice` /
disabled controls / documented notices), not frontend defects. None block Phase 19 completion —
D-04 in `19-CONTEXT.md` scopes integration to existing endpoints only, and D-05 requires gaps to
be shown as disabled/documented rather than faked.

| Gap | Affected module | UI treatment | Tracking |
|-----|------------------|---------------|----------|
| No `GET` list endpoint for admin menu categories/dishes | Menu | Reads `GET /menus/public` instead; inactive/archived items won't appear. | New — record as v1.1 backend follow-up if admin parity is needed. |
| No `GET` list endpoint for reservations | Tables | Session-local list of reservations created in the current session; clears on reload. | New — record as v1.1 backend follow-up. |
| `GET /admin/payments` has no status/method/date-range filters | Payments | Filter controls rendered but disabled. | Existing — backlog 999.1 (tracked in `.planning/STATE.md` Deferred Items and `PROJECT.md`). |
| No global admin order-listing endpoint | Orders | Action-only page (cancel by order/line ID) with an order-ID convenience panel sourced from the Kitchen board. | New — record as v1.1 backend follow-up. |
| No backend aggregate/summary endpoint for dashboard metrics | Overview | Metric cards render `-` placeholders with an explicit in-page notice explaining the gap; no data is faked. | New — record as v1.1 backend follow-up. |

None of these require backend changes to close out Phase 19 — the phase's D-04/D-05 decisions
explicitly scope Phase 19 to existing backend endpoints and require gaps to be surfaced, not
closed. They are listed here so they can be picked up as discrete backend work in a later
milestone.

---

## Success Criteria (from 19-03-PLAN.md)

- [x] Automated frontend verification passes — `npm run build` and `npm run test` both green.
- [x] Manual smoke path is documented — `admin-ui/README.md` "Manual Smoke Path" section.
- [x] Follow-up backend gaps are explicit — table above, cross-referenced with existing
      `.planning/STATE.md` deferred items and 19-02's `knownGaps` UI registry.

---

*Phase: 19-vuejs-admin-management-interface*
*Verification recorded: 2026-07-15*
