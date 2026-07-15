---
phase: 19-vuejs-admin-management-interface
verified: 2026-07-15T00:00:00Z
status: gaps_found
score: 11/12 must-haves verified
has_blocking_gaps: false
overrides_applied: 0
gaps:
  - truth: "Menu, table, inventory, payment, kitchen, and cancellation workflows expose role-aware affordances (ROADMAP.md Phase 19 Success Criterion 3)"
    status: failed
    severity: minor
    reason: >
      The frontend never reads or stores a user's role. No component in admin-ui/src decodes the
      JWT, calls a /me or /auth/sessions-style endpoint for role, or gates any control based on
      role. Backend SecurityConfig genuinely differentiates ADMIN-only endpoints (menu
      category/dish/topping-group/topping-option CRUD under /admin/menu/**, and dining-area/table
      CRUD under /admin/tables and /admin/tables/areas — both fall through to the catch-all
      `.requestMatchers("/admin/**").hasRole("ADMIN")`) from ADMIN+STAFF endpoints (inventory,
      payments, kitchen board, table occupancy/sessions/reservations, order cancellation). A STAFF
      user sees fully-enabled "New category", "New dish", "New area", "New table", and archive
      buttons in MenuView.vue/TablesView.vue with no visual distinction, and only discovers the
      restriction after submitting and receiving a generic 403 ApiError. This is reactive error
      handling (which IS implemented and covered by the "shared error/loading/empty states" half
      of the same success criterion), not proactive role-aware affordances.
    artifacts:
      - path: "admin-ui/src/stores/auth.ts"
        issue: "AuthSession only stores accessToken/refreshToken/tokenType — no role/username field is persisted or derived from the JWT, so no view can branch on role."
      - path: "admin-ui/src/views/MenuView.vue"
        issue: "Category/dish create and archive controls render unconditionally regardless of caller's role, even though the backing endpoints are ADMIN-only."
      - path: "admin-ui/src/views/TablesView.vue"
        issue: "Dining area/table CRUD controls render unconditionally regardless of caller's role, even though the backing endpoints are ADMIN-only."
    missing:
      - "Decode/derive role from the JWT (or add a lightweight /auth/session-style call) and store it in authState."
      - "Gate or visibly mark ADMIN-only controls (menu category/dish/topping CRUD, table/area CRUD) for STAFF sessions in MenuView.vue and TablesView.vue."
---

# Phase 19: VueJS Admin Management Interface Verification

**Phase Goal:** A Vue 3 + Vite admin app (separate front-end in `admin-ui/`) letting ADMIN/STAFF sign in (JWT session, persist/refresh/logout), and operate existing backend admin surfaces from one dense dashboard: menu, tables/table-ops, inventory/costing/stock, payments/refunds, kitchen board, and order cancellation/status. Consumes the Spring Boot API via a typed client with shared error/loading/empty states and role-aware affordances; env-configured API base URL; frontend verification for routing/auth-guards/API-client behavior plus a documented manual smoke path; backend API gaps documented (not silently mocked).

**Verified:** 2026-07-15
**Status:** gaps_found (one minor, non-blocking gap)
**Re-verification:** No — initial verification (a previous, non-standard `19-VERIFICATION.md` existed, authored by the 19-03 executor as a self-report; it lacked the goal-backward frontmatter/gap structure required here and is superseded by this report).

**Note on ROADMAP.md duplication:** `.planning/ROADMAP.md` contains two `### Phase 19` sections — one completed entry (line ~37, "Plans: 3/3 plans complete", empty `success_criteria` per `gsd-sdk query roadmap.get-phase`) and one stale backlog-style entry (line ~197, "Plans: 0 plans", "TBD (run /gsd-plan-phase 19...)") carrying an explicit 6-item Success Criteria list including "role-aware affordances". This is a known, already-flagged ROADMAP hygiene issue (see the "Reconcile in v1.1 planning" note near line 49). Because the phase-goal text supplied for this verification was built from that second block, this report treats all 6 of its Success Criteria as the authoritative contract and verifies against them, in addition to the three PLANs' frontmatter `must_haves`.

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Vue 3 + Vite + TypeScript app exists in `admin-ui/` | VERIFIED | `admin-ui/package.json` (vue, vite, typescript, vue-router, @lucide/vue, vitest); `npm run build` passes (`vue-tsc -b && vite build`, 181ms, emits `dist/`). |
| 2 | ADMIN/STAFF can log in, persist/refresh tokens, and log out | VERIFIED | `LoginView.vue` calls `login()` → `setSession()`; `stores/auth.ts` persists to `localStorage` (`restoreSession`/`setSession`/`clearSession`); `api/client.ts` performs one 401→refresh→retry, clears session and shows "Your session expired..." on refresh failure; `AdminLayout.vue signOut()` clears session + calls `POST /auth/logout`. Covered by 6 tests in `stores/auth.test.ts` + 2 refresh-path tests in `api/client.test.ts`. |
| 3 | Unauthenticated users are routed to `/login`; authenticated shell has no landing page, only the dense Overview workbench | VERIFIED | `router/index.ts beforeEach` redirects unauthenticated `requiresAuth` routes to `/login` with `redirect` query preserved; redirects authenticated users away from `/login` to `overview`. Confirmed by 4 tests in `router/index.test.ts`. `OverviewView.vue` is the `/` route — an operational metric grid, not marketing copy. |
| 4 | Admin shell has sidebar navigation for Overview, Menu, Tables, Inventory, Payments, Kitchen, Orders | VERIFIED | `AdminLayout.vue navItems` lists all 7; `router/index.ts` registers all 7 as child routes of the authenticated layout. |
| 5 | Shared API client attaches Bearer token and handles JSON errors consistently | VERIFIED | `api/client.ts apiFetch()` attaches `Authorization: Bearer` when `authState.accessToken` is set; `toApiError()` parses `{code,message}` JSON bodies and falls back to a generic `ApiError` on non-JSON bodies; 204 handled as `undefined`. 7 tests in `client.test.ts`. |
| 6 | Each admin module (Menu, Tables, Inventory, Payments, Kitchen, Orders) has a real route and view calling available backend endpoints through typed module API functions | VERIFIED | `api/modules.ts` defines `menuApi`/`tablesApi`/`inventoryApi`/`paymentsApi`/`kitchenApi`/`ordersApi`; every path/verb cross-checked 1:1 against the real Spring controllers (`AdminMenuController`, `AdminTableController`, `TableOperationController`, `InventoryController`, `InventoryStockController`, `PaymentController`, `KitchenController`, `AdminOrderCancellationController`) — no hallucinated endpoints found. Every module view (`MenuView`, `TablesView`, `InventoryView`, `PaymentsView`, `KitchenView`, `OrdersView`) calls its typed API module, not `fetch`/`apiFetch` directly. |
| 7 | Workflows expose shared error/loading/empty states | VERIFIED | All 6 module views use `ref loading`/`ref error` + `EmptyState`/skeleton pattern (`EmptyState.vue` has `tone="empty"/"error"` + retry emit; `DataTable.vue` renders `emptyText` when `rows.length === 0`). `OrdersView` (action-only, no list fetch) uses `DataTable`'s built-in empty text for its session-local results table instead. |
| 8 | Workflows expose role-aware affordances (ADMIN vs STAFF) | **FAILED** | See `gaps` in frontmatter. No role is decoded/stored anywhere in `admin-ui/src` (`grep -rn "role" src` matches only an unrelated `role="dialog"` ARIA attribute). Backend `SecurityConfig.java` genuinely restricts menu category/dish/topping CRUD and table/area CRUD to `ADMIN` only (falls through to the `/admin/**` catch-all `hasRole("ADMIN")`), while STAFF has access to inventory, payments, kitchen, table occupancy/sessions/reservations, and order cancellation. `MenuView.vue`/`TablesView.vue` render ADMIN-only controls identically for any authenticated role; STAFF only discovers the restriction via a reactive 403 error after submitting. |
| 9 | Vue 3 + Vite with maintainable structure, typed API boundary, env-configured API base URL | VERIFIED | `admin-ui/src/api/client.ts` reads `import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'`; clean `api/` / `components/` / `layouts/` / `router/` / `stores/` / `views/` / `lib/` structure; all request/response shapes are typed in `api/auth.ts` / `api/modules.ts`. |
| 10 | Backend gaps are surfaced as disabled/documented UI states, not silently mocked | VERIFIED | `api/modules.ts knownGaps` registry + `GapNotice.vue` render explicit in-page notices on Menu (category/dish listing gap), Tables (reservation listing gap), Payments (disabled status/method/date filters), Orders (no global order-listing endpoint). `OverviewView.vue` shows `-` placeholders with an explicit "Overview aggregate endpoints are not available yet" notice rather than fake numbers. |
| 11 | Frontend verification exists for routing/auth-guards/API-client behavior | VERIFIED | `npm run test` re-run during this verification: **3 files, 17 tests, 0 failures** (`stores/auth.test.ts` 6, `api/client.test.ts` 7, `router/index.test.ts` 4). Tests assert substantive behavior (401→refresh→retry-once call sequence, refresh-failure session clear, redirect-query preservation, authenticated-blocks-`/login`), not placeholders. |
| 12 | Manual smoke path documented; backend gaps listed as follow-up items | VERIFIED | `admin-ui/README.md` "Manual Smoke Path" section: 7 numbered steps covering unauthenticated redirect, login, all 7 module routes, one write action per module, and session-expiry redirect. "Known Backend Gaps" section lists the same 5 gaps as `19-02-SUMMARY.md`/`knownGaps`. |

**Score:** 11/12 truths verified (1 failed, severity: minor / non-blocking).

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `admin-ui/src/router/index.ts` | Auth guard + all module routes | VERIFIED | Guard + 7 child routes, wired, tested. |
| `admin-ui/src/stores/auth.ts` | Session persistence | VERIFIED | `setSession`/`clearSession`/`restoreSession`/`isAuthenticated`, tested. Missing: role field (see gap). |
| `admin-ui/src/api/client.ts` | Shared fetch wrapper | VERIFIED | Bearer attach, 401 refresh-retry-once, typed `ApiError`, tested. |
| `admin-ui/src/api/modules.ts` | Typed API wrappers, `knownGaps` | VERIFIED | All 6 module APIs, cross-checked against real backend controller paths. |
| `admin-ui/src/layouts/AdminLayout.vue` | Sidebar shell, sign-out | VERIFIED | 7 nav items, responsive drawer toggle, sign-out flow. |
| `admin-ui/src/views/{Menu,Tables,Inventory,Payments,Kitchen,Orders}View.vue` | Real, wired module pages | VERIFIED | Loading/error/empty states, forms, confirm dialogs, all calling typed API modules. |
| `admin-ui/README.md` | Setup + smoke path + gaps | VERIFIED | Present, copy-paste runnable commands, numbered smoke path, gap table. |
| `admin-ui/src/{stores,api,router}/*.test.ts` | Focused tests | VERIFIED | 17 tests, all pass on re-run. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `router/index.ts` guard | `stores/auth.ts isAuthenticated()` | direct import | WIRED | Guard reads live `authState.accessToken`. |
| `LoginView.vue` | `POST /auth/login` | `api/auth.ts login()` → `apiFetch` | WIRED | `retryOnUnauthorized: false` on login (correct — no session to refresh yet). |
| `AdminLayout.vue signOut()` | `POST /auth/logout` | `api/auth.ts logout()` | WIRED | Clears session first, then best-effort logout call. |
| Module views | Backend admin controllers | `api/modules.ts` typed wrappers | WIRED | Verified path-for-path against `AdminMenuController`/`AdminTableController`/`TableOperationController`/`InventoryController`/`InventoryStockController`/`PaymentController`/`KitchenController`/`AdminOrderCancellationController`. |
| `apiFetch` 401 handling | `POST /auth/refresh` | `refreshToken()` inline in `client.ts` | WIRED | Retried exactly once (`retryOnUnauthorized: false` on the retry), tested for both success and failure paths. |
| Auth session | Role-based UI gating | — | **NOT WIRED** | No such link exists; see gap. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `KitchenView.vue` | `board` | `kitchenApi.getBoard()` → `GET /admin/orders/kitchen-board` | Real backend query (no live backend in this sandbox; endpoint call is correct and unmocked) | FLOWING (by construction — no static/hardcoded fallback) |
| `TablesView.vue` | `occupancy`/`areas`/`tables` | `tablesApi.listOccupancy/listAreas/listTables()` | Real backend queries | FLOWING |
| `PaymentsView.vue` | `items` | `paymentsApi.listPayments()` → `GET /admin/payments` | Real backend query with cursor pagination | FLOWING |
| `InventoryView.vue` | `stock`/`movements` | `inventoryApi.listStock/listLowStock/listMovements()` | Real backend queries | FLOWING |
| `MenuView.vue` | `menu` | `menuApi.getPublicMenu()` → `GET /menus/public` | Real backend query (documented gap: public tree, not admin list) | FLOWING (with documented caveat, not faked) |
| `OverviewView.vue` | `metrics` | none — hardcoded `'-'` | No source; explicit in-page gap notice | STATIC (intentional, documented — no backend aggregate endpoint exists per D-04/D-05) |

No component was found silently rendering hardcoded/empty data without an accompanying `GapNotice`/explicit in-page explanation, except the intentionally-documented Overview placeholders.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Production build succeeds | `npm --prefix admin-ui run build` | `vue-tsc -b && vite build` — 181ms, `dist/index.html` + assets emitted, 0 typecheck errors | PASS |
| Unit test suite passes | `npm --prefix admin-ui run test` | `vitest run` — 3 files, 17 tests, 0 failures, 817ms | PASS |
| Live backend reachable in this sandbox | N/A | No Spring Boot/Postgres/Redis/Kafka process running here | SKIP (documented as the manual smoke path in `admin-ui/README.md`, per this phase's own D-06 decision — routed to human verification, not a gap) |

### Probe Execution

No `scripts/*/tests/probe-*.sh` convention or phase-declared probes found for this phase (`find scripts -path '*/tests/probe-*.sh'` and PLAN/SUMMARY grep both empty). Step 7c: SKIPPED — no probes declared.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|--------------|--------|----------|
| ADMIN-UI-001 | 19-01 | Vue app scaffold + login/session foundation | SATISFIED | Scaffold, `LoginView`, `stores/auth.ts`, `api/client.ts`. |
| ADMIN-UI-002 | 19-01, 19-02 | Admin shell + module routes calling backend | SATISFIED | `AdminLayout` + `router/index.ts` + 6 module views. |
| ADMIN-UI-003 | 19-02 | Module views with real backend calls | SATISFIED | `api/modules.ts` + wired views (see Key Link table). |
| ADMIN-UI-004 | 19-01 | Typed client, env-configured base URL | SATISFIED | `VITE_API_BASE_URL` in `client.ts`; typed request/response DTOs throughout `api/`. |
| ADMIN-UI-005 | 19-03 | Frontend verification (build/test/routing/auth-guard/API-client coverage) | SATISFIED | 17 tests across 3 files, `npm run build`/`run test` both green (re-verified). |
| ADMIN-UI-006 | 19-02, 19-03 | Backend gaps documented, not mocked | SATISFIED | `knownGaps` registry, `GapNotice`, README gap table, this VERIFICATION.md gap table. |

No orphaned requirement IDs — all 6 declared in ROADMAP.md (`[ADMIN-UI-001..006]`) are claimed by at least one plan's `requirements:` frontmatter, and evidence supports each.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` markers found in `admin-ui/src` | — | Clean — debt-marker gate has nothing to flag. |
| — | — | No `return null`/`return {}`/`return []`/`=> {}` stub patterns found outside test files | — | Clean. |
| `admin-ui/src/stores/auth.ts` | 6-17 | `AuthSession`/`authState` carry no role field | ℹ️ Info | Root cause of the role-aware-affordances gap above; not a code smell by itself, but the missing piece that would enable the fix. |

### Human Verification Required

### 1. Live-backend manual smoke path

**Test:** Follow `admin-ui/README.md` "Manual Smoke Path" against a running Spring Boot backend (Postgres/Redis/Kafka up) with a seeded ADMIN and a seeded STAFF account: unauthenticated redirect, login as each role, visit all 7 sidebar modules, exercise one write action per module, sign out / let a token expire.
**Expected:** Every module renders real backend data or a clear non-crashing error; the login→refresh→logout lifecycle behaves as described; STAFF gets a clear (if reactive) error when attempting an ADMIN-only action (category/dish/topping/area/table CRUD) rather than a silent failure or crash.
**Why human:** No Spring Boot backend, Postgres, Redis, or Kafka broker is available in this verification sandbox — this is consistent with what 19-01/19-02/19-03's own SUMMARY.md files report, and matches this phase's own D-06 decision to provide a documented manual smoke path rather than requiring a live-backend automated check.

### 2. Visual/responsive shell review

**Test:** Confirm the sidebar collapses to a mobile drawer under 768px, the topbar and dense tables read cleanly at desktop/tablet/mobile widths per `19-UI-SPEC.md`'s Responsive Contract, and the color/spacing/typography tokens match the approved design contract.
**Expected:** No overflow, consistent spacing scale, sidebar drawer toggle works on narrow viewports.
**Why human:** Visual/responsive rendering cannot be verified via static code/grep analysis alone.

### Gaps Summary

One gap was found: ROADMAP.md's Phase 19 Success Criterion 3 ("...call the existing backend endpoints with shared error/loading/empty states **and role-aware affordances**") is only half-satisfied. Shared error/loading/empty states are implemented consistently and well across every module. Role-aware affordances are not implemented at all — the frontend never derives or stores a user's role, so ADMIN-only controls (menu category/dish/topping-group/topping-option CRUD, dining-area/table CRUD — both restricted server-side to `ADMIN` via `SecurityConfig`'s `/admin/**` catch-all) render identically for STAFF users, who only discover the restriction via a reactive 403 error.

This is judged **severity: minor / non-blocking** because: (1) the backend correctly enforces the ADMIN/STAFF authorization boundary regardless of what the UI shows, so there is no security or data-integrity exposure; (2) the phase's actual planning artifacts — `19-CONTEXT.md`'s locked decisions D-01 through D-06 and all three PLANs' `must_haves` — never captured a role-aware-UI requirement, suggesting this ROADMAP success criterion originates from a stale, duplicate `### Phase 19` backlog block (see the note above the Goal Achievement section and ROADMAP.md's own "Reconcile in v1.1 planning" flag) rather than a requirement that was deliberately dropped during execution; (3) every other piece of the same success criterion (shared error/loading/empty states, real endpoint calls) is fully met.

**This looks intentional/pre-existing scope drift, not a build defect.** To accept this deviation, add to this file's frontmatter:

```yaml
overrides:
  - must_have: "Menu, table, inventory, payment, kitchen, and cancellation workflows expose role-aware affordances"
    reason: "Backend enforces ADMIN/STAFF authorization correctly; UI-level role gating was never captured in 19-CONTEXT.md's locked decisions or any of the three plans' must_haves — likely scope drift from a stale duplicate ROADMAP Phase 19 block, not a dropped requirement during execution. Track as a v1.1 polish follow-up instead of re-opening Phase 19."
    accepted_by: "<name>"
    accepted_at: "<ISO timestamp>"
```

Absent that override, the recommended next step is a small follow-up backlog item (not a full phase re-plan): store role on login (decode JWT or add a lightweight session-role read), and gate/mark the ADMIN-only controls in `MenuView.vue` and `TablesView.vue` for STAFF sessions.

---

*Verified: 2026-07-15*
*Verifier: Claude (gsd-verifier)*
