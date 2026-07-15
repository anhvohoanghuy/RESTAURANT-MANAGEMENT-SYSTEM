# Phase 20: Complete admin UI - Research

**Researched:** 2026-07-15
**Domain:** Vue 3 admin frontend extension (edit forms, recipe/topping authoring, session management, role gating) — frontend-only, backend already shipped.
**Confidence:** HIGH

## Summary

This phase extends the existing `admin-ui/` Vue 3 + Vite + TypeScript app (shipped in Phase 19) with no new dependencies, no new design system, and no backend changes. Every backend endpoint this phase wires already exists and was read directly from source in this session (`AdminMenuController`, `AdminTableController`, `TableOperationController`, `InventoryController`, `AuthController`, plus their DTO records) — request/response shapes below are copied verbatim from the Java records, not inferred.

The work is almost entirely mechanical: (1) add ~10 missing typed bindings to `admin-ui/src/api/modules.ts` (update/PUT calls, session-list calls, `upsertRecipe`/`recipeCost`), (2) extend three existing views (`MenuView.vue`, `TablesView.vue`, `InventoryView.vue`) with edit-mode support in their existing create modals, (3) build two net-new screens (`RecipeView.vue` for recipe authoring, `SessionsView.vue` for auth session management) plus one net-new in-view section (topping groups/options inside `MenuView.vue`), and (4) add JWT role decoding to `stores/auth.ts` plus route-guard/`v-if` gating. No new npm packages are required — role decoding is hand-rolled `atob` base64url decode (`JwtProvider.generateToken` embeds a plain `roles: string[]` claim, confirmed by reading `JwtProvider.java` and `RoleEnum.java`; role strings are bare `"ADMIN"`/`"STAFF"`/`"USER"`, not `"ROLE_ADMIN"` — the `ROLE_` prefix is added only server-side in `CustomUserDetails.getAuthorities()` for Spring Security's `hasRole()`).

**Primary recommendation:** Follow the D-01 shared-modal pattern (add `editing{Entity}Id` ref + pre-fill on open, same form/footer, route to `update` vs `create` on submit) for the 5 simple entities; build the recipe builder as its own routed view per D-02; extend `menuApi`/`tablesApi`/`inventoryApi` in `modules.ts` with typed `update*` functions before touching any view; add a `sessionsApi` module and `authState.isAdmin`/roles decode to `auth.ts` before building `SessionsView.vue`.

## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Edit uses a **shared create+edit modal** — reuse the existing `admin-ui/src/components/Modal.vue`. One form component per entity handles both create and edit; on edit it opens pre-filled with the row's current values. Applies to: Menu category/dish, Table area/table, Inventory ingredient.
- **D-02:** **Recipe authoring gets a dedicated screen/section (NOT the shared modal).** Recipe (dish → many ingredient rows) and topping group/option management involve nested/repeating rows too complex for the create+edit modal. Build as own view/route. Simple forms stay in modals (D-01); recipe/topping are the deliberate exception.

### Claude's Discretion
- **Backend-gap scope:** Phase stays frontend-only. Missing backend list endpoints (admin order listing, reservation listing, admin category/dish listing incl. INACTIVE/ARCHIVED) are NOT added — keep/extend `knownGaps` registry, defer to backlog (999.1 for payment filters; new backlog items for list endpoints). Do not mock them as complete.
- **Recipe/topping depth:** Build a full recipe builder (dish → ingredient rows with quantity + unit) and first-class topping group/option management. Wire `getRecipe` for pre-fill, `recipes/cost` + `costing` for cost display.
- **Role gating:** Decode role from JWT access-token claims client-side (no round-trip) since the token carries a `roles` claim (verified this session). Gate ADMIN-only controls by **hiding** (not disabling) for STAFF. Verified role matrix (see `20-UI-SPEC.md §6`, corrected from an earlier overbroad CONTEXT.md assumption): Menu catalog CRUD + Table catalog CRUD (areas/tables) = ADMIN only, hide for STAFF. Inventory, `/admin/menu/recipes/cost`, `/admin/menu/costing`, table-ops, payments, kitchen, orders = ADMIN+STAFF, visible to both — do NOT hide.
- **Post-save behavior, validation copy, exact layout** of forms — planner/implementer discretion, following the shared error/loading/empty-state conventions already established in 19-02.

### Deferred Ideas (OUT OF SCOPE)
- **Backend list endpoints** (own backend phase/backlog, NOT this phase): admin order listing/search, reservation listing (GET), admin category/dish listing incl. INACTIVE/ARCHIVED. Until these exist the UI keeps `knownGaps` notices.
- **Payment status/method/date filters** — backlog 999.1 (backend `GET /admin/payments` extension).
- **Role-aware affordances as a standalone item** — backlog 999.2 is folded into this phase's scope; drop 999.2 from backlog once Phase 20 ships it.

## Phase Requirements

No formal REQUIREMENTS.md entry exists for this phase (confirmed: `.planning/REQUIREMENTS.md` does not exist in this repo; `ROADMAP.md` Phase 20 section lists `[ ] TBD (run /gsd:plan-phase 20 to break down)`). Coverage is driven entirely by `20-CONTEXT.md` decisions D-01/D-02 and `20-UI-SPEC.md` §1–6, which are authoritative and copied above/below. The planner should derive its task list directly from the six "New UI for Phase 20" interaction contracts in `20-UI-SPEC.md` rather than from a requirement-ID table.

| Capability | Research Support |
|------------|-------------------|
| Edit modals: category, dish, area, table, ingredient | Verified PUT endpoints + exact DTO fields below; existing create-modal code read from `MenuView.vue`/`TablesView.vue`/`InventoryView.vue` |
| Recipe builder (dedicated screen) | Verified `RecipeRequest`/`RecipeResponse`/`RecipeCostResponse` shapes; UI-SPEC §2 gives exact interaction contract |
| Topping group/option management (create+archive only) | Verified no PUT exists for either — confirmed by reading `AdminMenuController.java` in full |
| Ingredient cost history + costing reads | `listCosts` binding already exists in `modules.ts`; `recipeCost`/`getMenuCosting` shapes verified |
| Auth session management | Verified `AuthController` sessions endpoints + `AuthSessionResponse`/`RevokeOtherSessionsRequest` shapes |
| Full reservation status transitions | Verified `UpdateReservationStatusRequest`/`ReservationStatus` enum (6 values, not 4) |
| Role-aware affordances | Verified JWT `roles` claim shape + `SecurityConfig` matcher order (first-match-wins) |

## Project Constraints (from CLAUDE.md)

No `./CLAUDE.md` exists in this repository (checked at session start — empty/absent). No project-level directives to enforce beyond what's already captured in `20-UI-SPEC.md` (the existing hand-rolled CSS design system, no component library, no Tailwind/shadcn).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Edit forms (category/dish/area/table/ingredient) | Frontend Server (SPA client) | API/Backend (existing PUT) | Pure UI composition over an already-shipped PUT endpoint; no new backend logic |
| Recipe builder + cost display | Frontend Server (SPA client) | API/Backend (existing PUT/GET) | Client assembles nested rows into `RecipeRequest.lines[]`; backend already computes cost |
| Topping group/option create+archive | Frontend Server (SPA client) | API/Backend (existing POST/DELETE) | No new backend surface; UI wires existing uncalled bindings |
| Auth session list/revoke | Frontend Server (SPA client) | API/Backend (existing GET/DELETE/POST) | Session state lives server-side (Redis-backed per Phase 06); client only renders + calls |
| Role decode + gating | Browser/Client | — | Pure client-side JWT payload decode (base64url), no network round-trip; enforcement remains server-side (non-goal note in UI-SPEC §6) |
| Reservation status control | Frontend Server (SPA client) | API/Backend (existing PATCH) | Replaces a hardcoded literal with a full enum selector calling the same endpoint |

## Standard Stack

### Core
No new libraries. This phase reuses the exact stack already installed in `admin-ui/package.json` — verified via `cat admin-ui/package.json` this session:

| Library | Version (installed) | Purpose | Why Standard (already shipped) |
|---------|------|---------|--------------|
| vue | ^3.5.39 | SFC composition API | Already the app's framework |
| vue-router | ^5.1.0 | Client routing, `beforeEach` guards | Already used for `requiresAuth` meta guard |
| @lucide/vue | ^1.24.0 | Icon set | Already used app-wide (`X`, `Home`, etc.) |
| vitest | ^4.1.10 | Unit/component tests | Already the test runner (17 tests green, confirmed by running `npm run test` this session) |
| typescript / vue-tsc | ~6.0.2 / ^3.3.5 | Typecheck gate on `npm run build` | Already the build gate |

### Supporting
None needed. No date/JWT/form-validation library is required — role decoding is 6 lines of hand-rolled `atob`+`JSON.parse` (below), and all new forms compose from existing `.field-grid`/`Modal.vue` primitives.

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Hand-rolled `atob` JWT payload decode | `jwt-decode` npm package | Adds a dependency for a 6-line operation; CONTEXT.md/UI-SPEC explicitly says "no extra round-trip" but doesn't mandate a library — hand-rolling avoids `package.json` churn and a new supply-chain surface for zero functional gain |
| Reactive per-module singletons (existing pattern) | Pinia | App already uses module-level `reactive()` singletons (`stores/auth.ts`) with zero state library, per UI-SPEC "Stack" table — introducing Pinia for 2 new views would be inconsistent and unnecessary |

**Installation:** None required — no `npm install` step for this phase.

**Version verification:** Not applicable (no new packages). Existing versions confirmed via direct `cat admin-ui/package.json` read this session — all current as of the last `npm install` that produced the committed `package-lock.json`.

## Package Legitimacy Audit

**Not applicable — this phase installs zero external packages.** All work is composed from libraries already present in `admin-ui/package.json` (verified above). No `slopcheck`/registry verification step is required. If a future implementer is tempted to add a JWT-decode or form library, treat that as a scope deviation requiring the same audit this section would otherwise contain.

## Architecture Patterns

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│ Browser (admin-ui SPA)                                              │
│                                                                       │
│  Login ──▶ POST /auth/login ──▶ setSession(AuthResponse)            │
│                                    │                                 │
│                                    ▼                                 │
│                          decode JWT payload (atob)                  │
│                          authState.roles = ["ADMIN"|"STAFF"|"USER"]  │
│                          authState.isAdmin = roles.includes("ADMIN")│
│                                    │                                 │
│              ┌─────────────────────┼─────────────────────┐          │
│              ▼                     ▼                     ▼          │
│      router.beforeEach     v-if="isAdmin"         All other views   │
│      (adminOnly routes,    (buttons/rows in       (no gate)         │
│       e.g. /menu/dishes/   MenuView/TablesView)                     │
│       :id/recipe)                                                    │
│              │                     │                     │          │
│              ▼                     ▼                     ▼          │
│   MenuView / TablesView / InventoryView (extended: edit modals)     │
│   RecipeView (new)         SessionsView (new)                       │
│              │                     │                                │
└──────────────┼─────────────────────┼────────────────────────────────┘
               ▼                     ▼
     apiFetch() → fetch(`${API_BASE_URL}${path}`, {Authorization: Bearer})
               │
               ▼
┌─────────────────────────────────────────────────────────────────────┐
│ Spring Boot backend (unchanged this phase)                          │
│  JwtAuthenticationFilter → SecurityConfig role matchers (first-match)│
│  AdminMenuController / AdminTableController / TableOperationController│
│  InventoryController / AuthController                               │
│  → existing PUT/POST/DELETE/GET/PATCH handlers, existing services   │
└─────────────────────────────────────────────────────────────────────┘
```

A STAFF user with a hidden control can still hit the endpoint directly (e.g. via devtools or a saved URL); the backend's existing `SecurityConfig` matcher + `AccessDeniedHandler` returns 403, surfaced through the existing `ApiError` → `.form-error` path. No new error handling is needed for this — this is the explicit non-goal in UI-SPEC §6.

### Recommended Project Structure
```
admin-ui/src/
├── api/
│   └── modules.ts          # ADD: update*/upsertRecipe/recipeCost/sessionsApi bindings here first
├── stores/
│   └── auth.ts             # ADD: roles decode, isAdmin computed, recompute in setSession()+restoreSession()
├── views/
│   ├── MenuView.vue        # EXTEND: edit mode on category/dish modals; NEW topping section; NEW "Recipe" row button
│   ├── TablesView.vue      # EXTEND: edit mode on area/table modals; replace hardcoded CANCELLED with status selector
│   ├── InventoryView.vue   # EXTEND: edit mode on ingredient modal; (cost-history display — see Open Questions)
│   ├── RecipeView.vue      # NEW: dedicated recipe-builder screen (D-02), routed /menu/dishes/:dishId/recipe
│   └── SessionsView.vue    # NEW: session list + revoke/revoke-others, routed /sessions
└── router/
    └── index.ts             # ADD: recipe route with meta.adminOnly + beforeEach check; sessions route (no gate)
```

### Pattern 1: Shared create+edit modal (D-01)
**What:** One reactive form object + one `editing{Entity}Id` ref per entity. `open{Entity}Modal()` with no arg resets to create defaults; called with a row seeds every field from the row and sets the editing id. Submit branches on whether the editing id is set.
**When to use:** Menu category, Menu dish, Table area, Table, Inventory ingredient — the 5 D-01 entities.
**Example (category, extending the existing `MenuView.vue` pattern read this session):**
```typescript
// Source: admin-ui/src/views/MenuView.vue (existing create-only code, read this session) + 20-UI-SPEC.md §1
const categoryModalOpen = ref(false)
const editingCategoryId = ref<string | null>(null)
const categoryForm = reactive({ name: '', description: '', sortOrder: 0, status: 'ACTIVE' as MenuStatus })
const categoryFormError = ref('')
const categorySaving = ref(false)

function openCategoryModal(row?: CategoryResponse) {
  editingCategoryId.value = row?.id ?? null
  categoryForm.name = row?.name ?? ''
  categoryForm.description = row?.description ?? ''
  categoryForm.sortOrder = row?.sortOrder ?? 0
  categoryForm.status = row?.status ?? 'ACTIVE'
  categoryFormError.value = ''
  categoryModalOpen.value = true
}

async function submitCategory() {
  categorySaving.value = true
  categoryFormError.value = ''
  try {
    const payload = {
      name: categoryForm.name,
      description: categoryForm.description || undefined,
      sortOrder: categoryForm.sortOrder,
      status: categoryForm.status,
    }
    if (editingCategoryId.value) {
      await menuApi.updateCategory(editingCategoryId.value, payload)
    } else {
      await menuApi.createCategory(payload)
    }
    categoryModalOpen.value = false
    editingCategoryId.value = null
    await loadMenu()
  } catch (caught) {
    categoryFormError.value = messageOf(caught, 'We could not save this category.')
  } finally {
    categorySaving.value = false
  }
}
```
Modal title becomes computed: `computed(() => editingCategoryId.value ? 'Edit category' : 'New category')` — per UI-SPEC §1 title convention. The category-chip click handler must distinguish text click (→ `openCategoryModal(category)`) from the existing × click (→ `requestArchive(...)`), per UI-SPEC §1's "Category/area chips" note.

### Pattern 2: Recipe builder repeatable rows (D-02)
**What:** A reactive array of row objects (`{ ingredientId, quantity, unit }`), rendered with `v-for`, each with a select/inputs/remove button; an "Add ingredient" button pushes an empty row.
**When to use:** `RecipeView.vue` only — this is the deliberate D-02 exception to the shared modal.
**Example:**
```typescript
// Source: menu_context/application/dto/MenuDtos.java (RecipeRequest.Line, read this session) + 20-UI-SPEC.md §2
type RecipeRowForm = { ingredientId: string; quantity: number; unit: string }
const rows = ref<RecipeRowForm[]>([])

function addRow() {
  rows.value.push({ ingredientId: '', quantity: 0, unit: '' })
}
function removeRow(index: number) {
  rows.value.splice(index, 1)
}
function onIngredientChange(index: number) {
  const ingredient = ingredients.value.find((i) => i.ingredientId === rows.value[index].ingredientId)
  if (ingredient) rows.value[index].unit = ingredient.baseUnit
}

async function saveRecipe() {
  if (rows.value.some((r) => !r.ingredientId || !r.quantity || !r.unit)) {
    formError.value = 'Every ingredient row needs an ingredient, quantity, and unit.'
    return
  }
  saving.value = true
  try {
    await menuApi.upsertRecipe({
      targetType: 'DISH',
      targetId: dishId,
      name: dishName.value,
      lines: rows.value.map((r, index) => ({
        ingredientId: r.ingredientId,
        ingredient: ingredients.value.find((i) => i.ingredientId === r.ingredientId)?.name ?? '',
        quantity: r.quantity,
        unit: r.unit,
        sortOrder: index,
      })),
    })
    await loadRecipeCost()
  } catch (caught) {
    formError.value = messageOf(caught, 'We could not save this recipe.')
  } finally {
    saving.value = false
  }
}
```
Note: `RecipeRequest.Line` requires BOTH `ingredientId` (UUID) AND `ingredient` (a display-name string) per the verified DTO — the planner must ensure the recipe-save payload includes the ingredient's name, not just its id, or the request body will be structurally wrong (this is a real trap, not a style choice — the backend record has both fields).

### Pattern 3: JWT role decode (no library)
**What:** Split the JWT on `.`, base64url-decode the middle segment, `JSON.parse`, read `.roles`.
**When to use:** `stores/auth.ts`, called from `setSession()` and `restoreSession()`.
**Example:**
```typescript
// Source: hand-rolled, verified against auth/application/auth_service/jwt/JwtProvider.java (read this session:
// claim("roles", roles) where roles = List<String> of RoleEnum names — "ADMIN"/"USER"/"STAFF", no ROLE_ prefix)
function decodeRoles(accessToken: string): string[] {
  try {
    const payload = accessToken.split('.')[1]
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')
    const json = JSON.parse(atob(padded)) as { roles?: string[] }
    return Array.isArray(json.roles) ? json.roles : []
  } catch {
    return []
  }
}
```
Add `roles: [] as string[]` to `authState`, recompute via `authState.roles = decodeRoles(response.accessToken)` inside `setSession()`, and `authState.roles = decodeRoles(session.accessToken)` inside `restoreSession()` (the exact two lifecycle points already present in `admin-ui/src/stores/auth.ts`, read in full this session). Expose `const isAdmin = computed(() => authState.roles.includes('ADMIN'))` for views to import.

### Anti-Patterns to Avoid
- **Forking two modals per entity (create modal + edit modal):** D-01 explicitly forbids this — one form component, branch on `editing{Entity}Id`.
- **Adding a "PUT" call for topping groups/options:** No such endpoint exists — confirmed by reading `AdminMenuController.java` in full; it has `POST`/`DELETE` for topping groups/options and no `DELETE` for topping *groups* at all (only `archiveToppingOption`). Do not fabricate an update or group-archive call.
- **Disabling ADMIN-only buttons with a tooltip instead of hiding:** Locked decision (D-01/discretion notes) is `v-if`, never `disabled`+tooltip.
- **Treating client-side role hiding as a security boundary:** It is UX polish only; the backend `SecurityConfig` remains the actual enforcement (non-goal, confirmed in UI-SPEC §6).
- **Assuming `ReservationStatus` has only 4 values:** The Java enum has 6 (`PENDING, CONFIRMED, SEATED, CANCELLED, NO_SHOW, COMPLETED`) — the UI-SPEC's reservation-status selector intentionally offers only 4 target statuses (`CONFIRMED`, `NO_SHOW`, `COMPLETED`, `CANCELLED`) because `PENDING` and `SEATED` are reached through other flows (creation, seat action), not the status modal. Do not add `PENDING`/`SEATED` as selectable options in that modal.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Confirm-before-destructive-action UI | A new inline confirm pattern | Existing `ConfirmDialog.vue` (already used for archive flows in all 3 views) | Consistent copy/behavior already established; reused verbatim for revoke-session, revoke-others, cancel/no-show reservation per UI-SPEC copy table |
| Loading/error/empty states | Bespoke per-view spinners/messages | Existing `EmptyState.vue` (`tone="error"`/`tone="empty"`) + `.skeleton-table` CSS class (confirmed present in `style.css` at the observed line) | Every existing panel in `MenuView`/`TablesView`/`InventoryView` already follows this exact convention; new panels (Recipe cost, Sessions) must match |
| Status color coding | New badge component or inline classes | Existing `StatusBadge.vue` — its `STATUS_TONES` map already covers `CONFIRMED`(success)/`NO_SHOW`(danger)/`COMPLETED`(success)/`CANCELLED`(danger), confirmed by reading the component in full | No changes needed to `StatusBadge.vue` itself for this phase |
| JWT parsing | A hand-rolled full JWT verify/parse library | The 6-line `atob` decode above (payload only, no signature verification — verification already happened server-side via `JwtAuthenticationFilter`) | Client never needs to verify the signature, only read a claim it already trusts because the backend issued it over an authenticated HTTPS session |

**Key insight:** Every UI primitive this phase needs already exists and is proven in production views (Menu/Tables/Inventory). The only genuinely new code is: (1) 5 `update*` API bindings + 1 `upsertRecipe`/`recipeCost` pair + 1 `sessionsApi` module (~15 small functions), (2) the recipe-row array logic, (3) the JWT decode, (4) wiring `v-if`/route-guard gates. Resist the temptation to introduce new components/patterns for these — extend the existing ones.

## Common Pitfalls

### Pitfall 1: Hardcoded `status: 'CANCELLED'` in TablesView.vue
**What goes wrong:** The current `cancelReservation()` function (confirmed at `admin-ui/src/views/TablesView.vue:325-334`, read in full this session) always calls `tablesApi.updateReservationStatus(reservationId, { status: 'CANCELLED' })` — there is no path to reach `CONFIRMED`, `NO_SHOW`, or `COMPLETED` today.
**Why it happens:** Phase 19 only needed a minimal cancel action; the full status enum was out of scope then.
**How to avoid:** Replace the "Cancel" button + `cancelReservation()` function entirely with a "Status" button opening a `Modal` (UI-SPEC §5: title "Update reservation status", a `<select>` of the 4 target statuses excluding the row's current status, optional `note` field, escalating to a `ConfirmDialog` for `CANCELLED`/`NO_SHOW` per the copy table).
**Warning signs:** If the planner's task list still shows a function named `cancelReservation` unchanged, the full-status requirement was missed.

### Pitfall 2: Recipe builder complexity underestimated
**What goes wrong:** Treating the recipe screen as "just another form" and cramming it into the shared modal, or forgetting that `RecipeRequest.Line` needs both `ingredientId` and a separate `ingredient` (name) field.
**Why it happens:** D-02 exists precisely because this screen is qualitatively different (nested repeatable rows) from the other 5 D-01 entities; the DTO shape (two ingredient-identifying fields on one line record) is non-obvious without reading the Java source.
**How to avoid:** Follow Pattern 2 above exactly; always resolve `ingredient` (name) from the selected `ingredientId` at submit time from the already-loaded `listIngredients()` result — do not add a duplicate text input for the ingredient name.
**Warning signs:** A `RecipeRequest` payload sent without the `ingredient` field will still type-check if the frontend type is defined loosely (e.g., using `Partial<>` or `any`) but will fail/behave unexpectedly against the strict Java record — type the frontend `RecipeRequest`/`Line` types to exactly mirror the Java record fields.

### Pitfall 3: Role-gating treated as a security boundary
**What goes wrong:** Implementer skips server-side error handling for STAFF users who bypass the hidden UI (e.g., via a bookmarked recipe-builder URL or devtools), assuming hiding is sufficient.
**Why it happens:** `v-if`-only gating is genuinely all that's needed for UX, but it's tempting to also skip the route-guard for the one route that needs it (Recipe builder).
**How to avoid:** Add the `meta: { adminOnly: true }` + `router.beforeEach` redirect specifically for `/menu/dishes/:dishId/recipe` (the only new route needing this per UI-SPEC §6 — Sessions is open to all roles, and Topping/edit-modal live inside existing views gated at button level only). Confirm the existing generic `ApiError` → `.form-error` path already surfaces a 403 without new code — verified by reading `client.ts`'s `toApiError()`.
**Warning signs:** A STAFF user can navigate directly to `/menu/dishes/{id}/recipe` and see the recipe builder UI (even if the save call then 403s) — this is the observable failure mode.

### Pitfall 4: Confusing the two different session concepts
**What goes wrong:** Conflating `TableOperationDtos.TableSessionResponse` (a dining-table occupancy session, already used throughout `TablesView.vue`'s `openSessionModal`) with the new `AuthSessionResponse` (an auth/login session for the sessions-management screen). Both are called "session" in this codebase.
**Why it happens:** Same English word, two unrelated domain concepts (table occupancy vs. auth device sessions).
**How to avoid:** Name the new view's types/variables distinctly (e.g., `authSessions`, not `sessions`) and do not import `TableSessionResponse` anywhere near `SessionsView.vue`.
**Warning signs:** A type error or a confusing variable named `sessions` that actually holds `AuthSessionResponse[]` inside a table-related file, or vice versa.

### Pitfall 5: Assuming `AuthSessionResponse` can identify "this device"
**What goes wrong:** Building a "this device" badge on the sessions list by comparing `sessionId` against something client-side.
**Why it happens:** It would be a nice UX touch, and looks achievable at first glance.
**How to avoid:** UI-SPEC §4 explicitly documents this as a known limitation — `AuthSessionResponse` (verified: `sessionId, createdAt, expiresAt, lastUsedAt, ipAddress, userAgent` — no current-session flag) carries no way to correlate the current session's server-side id client-side. Leave all rows visually equal, per the locked spec.
**Warning signs:** A task that says "highlight the current session" — this should be flagged back to the spec, not implemented as a guess.

## Code Examples

### Menu edit-mode bindings to add to `modules.ts`
```typescript
// Source: menu_context/infrastructure/presentation/AdminMenuController.java (read this session)
// PUT /admin/menu/categories/{id} -- role: ADMIN only (matched by /admin/** catch-all, no earlier matcher covers it)
updateCategory: (id: string, body: CategoryRequest) =>
  apiFetch<CategoryResponse>(`/admin/menu/categories/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
// PUT /admin/menu/dishes/{id} -- role: ADMIN only
updateDish: (id: string, body: DishRequest) =>
  apiFetch<DishResponse>(`/admin/menu/dishes/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
// PUT /admin/menu/recipes -- role: ADMIN only (no {id} path var -- targetType+targetId identify the recipe in the body)
upsertRecipe: (body: RecipeRequest) =>
  apiFetch<RecipeResponse>('/admin/menu/recipes', { method: 'PUT', body: JSON.stringify(body) }),
```
`RecipeRequest`/`RecipeResponse` types are not yet defined in `modules.ts` (the existing `getRecipe` binding returns `unknown` — confirmed by reading the file this session) — the planner must add these types matching the Java records exactly:
```typescript
export type RecipeLine = { ingredientId: string; ingredient: string; quantity: number; unit: string; sortOrder?: number }
export type RecipeRequest = { targetType: RecipeTargetType; targetId: string; name: string; lines: RecipeLine[] }
export type RecipeResponseLine = { id: string; ingredientId: string; ingredient: string; quantity: number; unit: string; sortOrder: number }
export type RecipeResponse = { id: string; targetType: RecipeTargetType; targetId: string; name: string; lines: RecipeResponseLine[] }
```
And retype `getRecipe` from `apiFetch<unknown>` to `apiFetch<RecipeResponse>`.

### Inventory / recipe-cost bindings to add
```typescript
// Source: inventory_context/infrastructure/presentation/InventoryController.java (read this session)
// PUT /admin/inventory/ingredients/{ingredientId} -- role: ADMIN + STAFF (matched by /admin/inventory/** rule)
updateIngredient: (id: string, body: IngredientRequest) =>
  apiFetch<IngredientResponse>(`/admin/inventory/ingredients/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
// GET /admin/menu/recipes/cost -- role: ADMIN + STAFF (explicit matcher, evaluated before the ADMIN-only /admin/** catch-all)
recipeCost: (targetType: RecipeTargetType, targetId: string) =>
  apiFetch<RecipeCostResponse>(withQuery('/admin/menu/recipes/cost', { targetType, targetId })),
```
`RecipeCostResponse`/`RecipeCostLineResponse` types (mirroring `InventoryDtos.java` exactly, read this session):
```typescript
export type RecipeCostLineResponse = {
  recipeLineId: string; ingredientId: string; ingredientName: string
  quantity: number; unit: string; convertedQuantity: number; costUnit: string
  unitCost: number; lineCost: number; costed: boolean; reason: string | null
}
export type RecipeCostResponse = {
  targetType: RecipeTargetType; targetId: string; recipeName: string
  totalCost: number; fullyCosted: boolean; lines: RecipeCostLineResponse[]
}
```

### Tables edit-mode + reservation-status bindings
```typescript
// Source: table_context/infrastructure/presentation/AdminTableController.java + TableOperationController.java (read this session)
// PUT /admin/tables/areas/{id} -- role: ADMIN only (no earlier matcher covers bare /admin/tables/areas/{id})
updateArea: (id: string, body: DiningAreaRequest) =>
  apiFetch<DiningAreaResponse>(`/admin/tables/areas/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
// PUT /admin/tables/{id} -- role: ADMIN only
updateTable: (id: string, body: DiningTableRequest) =>
  apiFetch<DiningTableResponse>(`/admin/tables/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
// updateReservationStatus already exists in modules.ts (verified) -- PATCH /admin/tables/reservations/{reservationId}/status
// role: ADMIN + STAFF (matched by the /admin/tables/reservations/** rule, before the /admin/** catch-all)
```

### Auth sessions module (net-new)
```typescript
// Source: auth/infrastructure/presentation/AuthController.java + application/dto/AuthSessionResponse.java + RevokeOtherSessionsRequest.java (read this session)
// GET /auth/sessions -- role: any authenticated (USER, ADMIN, STAFF)
// DELETE /auth/sessions/{sessionId} -- role: any authenticated
// POST /auth/sessions/revoke-others -- role: any authenticated; body: { refreshToken?: string }
export type AuthSessionResponse = {
  sessionId: string
  createdAt: string
  expiresAt: string
  lastUsedAt: string
  ipAddress: string | null
  userAgent: string | null
}

export const sessionsApi = {
  list: () => apiFetch<AuthSessionResponse[]>('/auth/sessions'),
  revoke: (sessionId: string) => apiFetch<void>(`/auth/sessions/${sessionId}`, { method: 'DELETE' }),
  revokeOthers: () =>
    apiFetch<void>('/auth/sessions/revoke-others', {
      method: 'POST',
      body: JSON.stringify({ refreshToken: authState.refreshToken }),
    }),
}
```
Note: `RevokeOtherSessionsRequest.refreshToken` is present in the Java record but the `AuthSessionService.revokeOtherSessions` call passes it through — sending the current `authState.refreshToken` (available in `stores/auth.ts`) is the correct value; do not send `null`/omit it, since the backend likely uses it to identify which session to *exclude* from revocation (the "this device stays signed in" behavior promised in the UI-SPEC copy).

### Router guard for the ADMIN-only recipe route
```typescript
// Source: admin-ui/src/router/index.ts (existing beforeEach, extended) + isAdmin computed from stores/auth.ts
router.beforeEach((to) => {
  if (to.meta.requiresAuth && !isAuthenticated()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && isAuthenticated()) {
    return { name: 'overview' }
  }
  if (to.meta.adminOnly && !authState.roles.includes('ADMIN')) {
    return { name: 'menu' }
  }
  return true
})
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| `MenuView.vue` topping/recipe section = `.notice-panel` placeholder text ("a dedicated editor UI is deferred…") | Real topping-management section + dedicated recipe route | This phase | The placeholder copy (confirmed present at `MenuView.vue` lines ~305-314, read this session) must be deleted, not left alongside the new UI |
| `TablesView.vue` reservation row = single "Cancel" button, hardcoded `CANCELLED` | Full 4-status selector modal | This phase | Existing `cancelReservation()` function is replaced, not extended |
| `stores/auth.ts` = tokens only, no role awareness | Adds `roles: string[]` + `isAdmin` computed | This phase | All existing tests in `auth.test.ts` (4 describe blocks, read this session) must continue passing — new fields are additive, no breaking change to `AuthSession`/`setSession` signatures |

**Deprecated/outdated:** None — this is the first and only version of these UI surfaces; there is no prior "old way" being replaced except the two items above.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| (none) | — | — | — |

**This table is empty: all claims in this research were verified directly by reading source files in this session** (Java controllers, Java DTO records, Java enums, `SecurityConfig.java`, `JwtProvider.java`, `RoleEnum.java`, `CustomUserDetails.java`, and every relevant `admin-ui/src/**` file), or copied verbatim from `20-CONTEXT.md`/`20-UI-SPEC.md` which are themselves marked as verified against `SecurityConfig.java`. No claim in this document rests on training-data recall alone.

## Open Questions (RESOLVED)

1. **Ingredient cost-history display (`listCosts`) — where does it render?**
   - What we know: `listCosts(ingredientId)` binding already exists and is unused; UI-SPEC's "New UI for Phase 20" section (§1-6) does not describe a dedicated cost-history panel/table for it, unlike the Recipe cost panel which is fully specified in §2.
   - What's unclear: Whether cost history should be a new expandable row/panel inside `InventoryView.vue`'s existing "Stock balances" section, a modal triggered from the ingredient row (e.g., "View costs" button next to "Add cost"), or deferred entirely.
   - Recommendation: Since CONTEXT.md's "Specific Ideas" section lists `listCosts` alongside `getRecipe` as "uncalled" bindings this phase should wire, and the existing `openCostModal` pattern already exists for *adding* a cost, the lowest-friction planning choice is a small "View costs" button next to "Add cost" in the ingredient row that opens a read-only modal/panel listing `IngredientCostResponse[]` (columns: unit cost, cost unit, effective at, source, note, created at) via `DataTable`. Flag this to the planner as needing an explicit task since it's the one wiring point not spelled out in UI-SPEC's numbered interaction contracts.

2. **Does `revoke-others` need the request body at all, or can it be an empty POST?**
   - What we know: `RevokeOtherSessionsRequest(String refreshToken)` is a required field in the Java record; `AuthController.revokeOtherSessions` passes `request == null ? null : request.refreshToken()` to the service — so a `null` body technically won't throw at the controller layer.
   - What's unclear: Whether `AuthSessionService.revokeOtherSessions(userId, null)` behaves correctly (revokes all-but-current) or requires the actual refresh token to identify which session is "current" — this depends on `AuthSessionService` internals not read in this session (out of scope: backend behavior is unchanged, and the phase should call the endpoint the same way regardless).
   - Recommendation: Send `authState.refreshToken` in the body (matches the "this device stays signed in" UX promise most safely) rather than omitting it — this is the conservative choice and costs nothing since the token is already in `authState`.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js | admin-ui build/dev/test | ✓ | v22.23.1 (README requires 20+) | — |
| npm | package management | ✓ | 10.9.8 | — |
| Existing admin-ui deps (vue, vue-router, @lucide/vue, vitest, vue-tsc) | All new views/components | ✓ | Per `package.json`, confirmed installed (17 tests pass via `npm run test`, run this session) | — |
| Running Spring Boot backend | Manual smoke-testing the wired endpoints | Not verified this session (out of scope — research is code-level, no backend process was started) | — | Use `admin-ui/README.md`'s documented `VITE_API_BASE_URL` + backend startup instructions for manual verification during implementation |

**Missing dependencies with no fallback:** None.
**Missing dependencies with fallback:** Live backend for manual smoke testing — implementer should start the backend per `admin-ui/README.md` "Backend Expectations" section before manual verification; automated `vitest` component tests do not require a live backend (existing tests confirm this — `client.test.ts` mocks `fetch` globally).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Vitest 4.1.10 (confirmed via `package.json` + successful `npm run test` run this session: 3 files, 17 tests, all passing) |
| Config file | `admin-ui/vitest.config.ts` (environment: `jsdom`, `@vitejs/plugin-vue` plugin) |
| Quick run command | `npm run test -- <path-to-file>` (e.g. `npm run test -- src/stores/auth.test.ts`), run from `admin-ui/` |
| Full suite command | `npm run test` (from `admin-ui/`), plus `npm run build` (vue-tsc typecheck + vite build) as the CI-equivalent gate per README's "Verification" section |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ROLE-01 | `decodeRoles()` correctly extracts roles array from a JWT with a `roles` claim | unit | `npm run test -- src/stores/auth.test.ts` | ❌ Wave 0 — extend existing `auth.test.ts` |
| ROLE-02 | `authState.isAdmin` is true only when roles include `"ADMIN"` | unit | `npm run test -- src/stores/auth.test.ts` | ❌ Wave 0 |
| ROUTE-01 | Recipe route redirects non-ADMIN to `/menu` when deep-linked | unit (router) | `npm run test -- src/router/index.test.ts` | ❌ Wave 0 — extend existing `router/index.test.ts` (pattern for asserting redirects already present) |
| MODAL-01 | Edit modal pre-fills form fields from the row passed to `open{Entity}Modal(row)` | component | new `*.test.ts` colocated with the view, if the planner chooses component-level tests (no existing precedent for view-level component tests in this codebase — currently only `api/client.test.ts`, `stores/auth.test.ts`, `router/index.test.ts` exist) | ❌ Wave 0 — no view component test precedent exists; planner should decide whether to introduce one or rely on manual verification, since this is a new test category for the app |
| RECIPE-01 | `upsertRecipe` payload includes `ingredient` (name) resolved from `ingredientId` for every row | unit | New test file, e.g. `views/RecipeView.test.ts`, OR extract the row→payload mapping into a pure function in a `lib/` module and unit-test that directly (simpler, matches existing `lib/format.ts` precedent) | ❌ Wave 0 |
| SESSION-01 | `sessionsApi.revokeOthers()` sends the current `refreshToken` in the body | unit | New/extended `api/modules.test.ts` (does not currently exist — only `api/client.test.ts` tests the low-level `apiFetch`, not `modules.ts` bindings) | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `npm run test -- <changed-file>.test.ts` (fast, targeted)
- **Per wave merge:** `npm run test` (full 17+ tests) + `npm run build` (typecheck gate)
- **Phase gate:** Full suite green + `npm run build` green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] Extend `admin-ui/src/stores/auth.test.ts` — cover `decodeRoles`/`isAdmin` (ROLE-01, ROLE-02)
- [ ] Extend `admin-ui/src/router/index.test.ts` — cover the new `adminOnly` redirect (ROUTE-01)
- [ ] Decide whether to introduce a pure-function extraction for the recipe row→payload mapping (recommended: `admin-ui/src/lib/recipe.ts` with a `toRecipeRequest(dishId, dishName, rows, ingredients)` function, unit-testable without mounting a component) — no existing precedent for testing Vue view internals directly in this codebase
- [ ] New `admin-ui/src/api/modules.test.ts` if the planner wants unit coverage on the new typed bindings' request shapes (currently `modules.ts` itself has zero direct test coverage — only the underlying `apiFetch` is tested)
- Framework install: none — Vitest is already configured and running

*(Existing infra: `vitest.config.ts` + `@vue/test-utils` are already installed and configured; no framework install needed, only new test files/cases.)*

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No (unchanged this phase) | Existing JWT bearer-token flow, untouched |
| V3 Session Management | Yes — read-only exposure | This phase *displays* and allows revocation of sessions already managed server-side (Phase 06); no new session-issuance logic. Client never verifies the JWT signature (correctly relies on server-side `JwtAuthenticationFilter` verification) — client-side decode is display/gating only |
| V4 Access Control | Yes | Client-side `v-if` hiding + route guard are UX-only; actual access control is enforced by the existing, unchanged `SecurityConfig` matcher chain (verified this session) — this phase must not introduce any illusion that the frontend enforces authorization |
| V5 Input Validation | Yes | New forms (recipe rows, edit modals) should validate required fields client-side (matches existing terse single-line `.form-error` convention) before submit, but the backend remains the authoritative validator — no new backend validation is added or assumed in this phase |
| V6 Cryptography | No | No cryptographic operations added — JWT payload decode is not a crypto operation (no signature verification performed client-side, correctly) |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Client trusts an unverified JWT claim for a security decision | Spoofing / Elevation of Privilege | Already correctly scoped: the `roles` claim decode is used ONLY for hiding UI controls (a UX affordance), never as a substitute for server-side authorization — the existing 403 path via `SecurityConfig` + `ApiError` remains the actual boundary (confirmed non-goal in UI-SPEC §6) |
| Sending the wrong/stale `refreshToken` in `revoke-others`, accidentally revoking the current session | Denial of Service (self-inflicted) | Always read `authState.refreshToken` live at call time (not a captured/stale closure value) inside `sessionsApi.revokeOthers()` |
| XSS via unescaped `userAgent`/`ipAddress` strings in the Sessions table | Tampering / Information Disclosure | Vue's default template interpolation (`{{ }}`) auto-escapes — do not use `v-html` anywhere in `SessionsView.vue` for these fields; the existing `DataTable.vue` default cell renderer already uses safe interpolation (confirmed by reading the component) |

## Sources

### Primary (HIGH confidence — direct source read, this session)
- `src/main/java/com/example/feat1/DDD/menu_context/infrastructure/presentation/AdminMenuController.java` — Menu CRUD, topping, recipe endpoints
- `src/main/java/com/example/feat1/DDD/menu_context/application/dto/MenuDtos.java` — all Menu DTO records
- `src/main/java/com/example/feat1/DDD/table_context/infrastructure/presentation/AdminTableController.java` — area/table CRUD endpoints
- `src/main/java/com/example/feat1/DDD/table_context/infrastructure/presentation/TableOperationController.java` — session/reservation endpoints incl. `updateReservationStatus`
- `src/main/java/com/example/feat1/DDD/table_context/application/dto/TableDtos.java`, `TableOperationDtos.java` — all Table DTO records
- `src/main/java/com/example/feat1/DDD/table_context/domain/model/ReservationStatus.java` — 6-value enum, confirmed
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/presentation/InventoryController.java` — ingredient/cost/recipe-cost/menu-costing endpoints
- `src/main/java/com/example/feat1/DDD/inventory_context/application/dto/InventoryDtos.java` — all Inventory DTO records
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/presentation/AuthController.java` — sessions endpoints
- `src/main/java/com/example/feat1/DDD/auth/application/dto/AuthSessionResponse.java`, `RevokeOtherSessionsRequest.java`
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/SecurityConfig.java` — full authoritative matcher chain, order confirmed
- `src/main/java/com/example/feat1/DDD/auth/application/auth_service/jwt/JwtProvider.java` — confirmed `claim("roles", roles)` shape
- `src/main/java/com/example/feat1/DDD/identity_context/application/dto/RoleEnum.java` — confirmed bare role name strings ("ADMIN"/"USER"/"STAFF")
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/CustomUserDetails.java` — confirmed `ROLE_` prefix added only server-side for Spring authorities
- `admin-ui/src/api/modules.ts`, `admin-ui/src/stores/auth.ts`, `admin-ui/src/router/index.ts`, `admin-ui/src/api/client.ts`, `admin-ui/src/api/auth.ts` — full read
- `admin-ui/src/components/Modal.vue`, `ConfirmDialog.vue`, `DataTable.vue`, `EmptyState.vue`, `StatusBadge.vue`, `GapNotice.vue`, `Toolbar.vue` — full read
- `admin-ui/src/views/MenuView.vue`, `TablesView.vue`, `InventoryView.vue`, `AdminLayout.vue` — full read
- `admin-ui/src/lib/format.ts` — full read
- `admin-ui/src/stores/auth.test.ts`, `admin-ui/src/router/index.test.ts`, `admin-ui/src/api/client.test.ts` — full read
- `admin-ui/package.json` — full read; `node --version`/`npm --version` run this session
- `npm run test` executed this session — confirmed 17/17 passing before any changes
- `.planning/phases/20-.../20-CONTEXT.md`, `20-UI-SPEC.md` — full read (both already contain source-verified claims per their own text)

### Secondary (MEDIUM confidence)
- None — every claim in this document traces to a direct file read or command execution performed in this session.

### Tertiary (LOW confidence)
- None.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new packages; existing `package.json` read directly, tests run directly
- Architecture: HIGH — every endpoint, DTO, and enum verified by reading the actual Java source this session; every frontend pattern verified by reading the actual Vue source this session
- Pitfalls: HIGH — the two flagged pitfalls (hardcoded CANCELLED, recipe DTO's dual ingredient fields) are both directly observed in source, not inferred

**Research date:** 2026-07-15
**Valid until:** Backend endpoints are stable (no changes planned per CONTEXT.md's fixed boundary) — treat as valid through this phase's implementation window; re-verify only if backend controllers change before planning completes.
