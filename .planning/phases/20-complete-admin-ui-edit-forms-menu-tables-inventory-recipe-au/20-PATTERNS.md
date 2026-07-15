# Phase 20: Complete admin UI - Pattern Map

**Mapped:** 2026-07-15
**Files analyzed:** 12 (7 extended, 3 net-new, 2 test-only)
**Analogs found:** 11 / 12 (RecipeView.vue's repeatable-row mechanic has no in-codebase analog — flagged below)

All files are under `admin-ui/src/` (Vue 3.5 + Vite + TS + vue-router 5 + vitest; no state library — module-level `reactive()` singletons). No backend files are touched this phase.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `admin-ui/src/api/modules.ts` | service (typed API bindings) | CRUD (mixed PUT/GET/POST/DELETE) | itself — existing `menuApi`/`tablesApi`/`inventoryApi` conventions in the same file | exact (extend in place) |
| `admin-ui/src/stores/auth.ts` | store (reactive singleton) | transform (JWT decode) + CRUD (session state) | itself — existing `setSession`/`restoreSession` lifecycle | exact (extend in place) |
| `admin-ui/src/stores/auth.test.ts` | test (unit) | — | itself — existing `describe` blocks | exact (extend in place) |
| `admin-ui/src/router/index.ts` | route/config | request-response (guard) | itself — existing `router.beforeEach` | exact (extend in place) |
| `admin-ui/src/router/index.test.ts` | test (unit/router) | — | itself — existing redirect-assertion tests | exact (extend in place) |
| `admin-ui/src/layouts/AdminLayout.vue` | provider/nav shell | request-response (nav) | itself — existing `navItems` array | exact (extend in place) |
| `admin-ui/src/views/MenuView.vue` | component/view | CRUD (edit modals) + CRUD (topping create+archive) | itself (create-only pattern) + `TablesView.vue` (chip-text-vs-×-click distinction) | exact (extend) / role-match (topping create+archive borrows Payments' modal+row-action shape) |
| `admin-ui/src/views/TablesView.vue` | component/view | CRUD (edit modals) + event-driven (status transition) | itself — `openSessionModal`'s multi-mode reactive-object modal is the closest analog for a multi-target-status selector modal | exact (edit modals) / role-match (status selector reuses multi-mode-modal shape, not a literal analog since it's single-mode) |
| `admin-ui/src/views/InventoryView.vue` | component/view | CRUD (edit modal) + CRUD (cost-history read) | itself — existing `openCostModal`/`costForm` add-cost pattern | exact (edit modal) / role-match (cost-history view has no read-only-list precedent in this view, closest is `DataTable` read-only usage in `PaymentsView.vue`) |
| `admin-ui/src/views/RecipeView.vue` (NEW) | view (dedicated screen, D-02) | CRUD + transform (nested repeatable rows → flat payload) | Page chrome: `MenuView.vue`/`InventoryView.vue` panel layout. Row auto-fill-on-select: `InventoryView.vue`'s `onMovementIngredientChange`. **No repeatable-`v-for`-row-array analog exists anywhere in `admin-ui/src/`** | role-match for chrome/loading/error/empty states and the auto-fill-unit sub-pattern; **NO ANALOG** for the row-array add/remove mechanic itself — net-new design (see below) |
| `admin-ui/src/views/SessionsView.vue` (NEW) | view (simple list + revoke) | CRUD (list, delete-one, action-all) | `PaymentsView.vue` — load-list-on-mount + `DataTable` + toolbar-level bulk action + row-level per-item action opening a modal/confirm | exact (structural match — closest existing "simple list view" in the six existing views) |
| `admin-ui/src/lib/recipe.ts` (NEW, optional but recommended) | utility (pure function) | transform | `admin-ui/src/lib/format.ts` — small pure exported functions, no side effects | role-match |
| `admin-ui/src/api/modules.test.ts` (NEW, optional) | test (unit) | — | `admin-ui/src/api/client.test.ts` — `vi.stubGlobal('fetch', ...)` mock-fetch pattern | role-match |

---

## Pattern Assignments

### `admin-ui/src/api/modules.ts` (service, CRUD) — EXTEND

**Analog:** itself (existing `menuApi`/`tablesApi`/`inventoryApi` export-object convention, lines 150-168 / 264-306 / 389-409)

**Convention to copy exactly** — every module is a `const xApi = { ... }` object literal of arrow functions calling `apiFetch<ResponseType>(path, { method, body: JSON.stringify(body) })`, with request/response types declared immediately above as plain `type` aliases (not classes/interfaces with methods):

```typescript
// Source: admin-ui/src/api/modules.ts lines 264-273 (tablesApi, existing)
export const tablesApi = {
  listAreas: () => apiFetch<DiningAreaResponse[]>('/admin/tables/areas'),
  createArea: (body: DiningAreaRequest) =>
    apiFetch<DiningAreaResponse>('/admin/tables/areas', { method: 'POST', body: JSON.stringify(body) }),
  archiveArea: (id: string) => apiFetch<DiningAreaResponse>(`/admin/tables/areas/${id}`, { method: 'DELETE' }),
  ...
```

**Update-call shape to add** (mirrors the existing `create*`/`archive*` calls, just swap verb+path):
```typescript
updateCategory: (id: string, body: CategoryRequest) =>
  apiFetch<CategoryResponse>(`/admin/menu/categories/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
```

**`withQuery` helper already exists** (lines 3-16) — reuse for `recipeCost(targetType, targetId)`'s querystring, exactly like the existing `getRecipe` binding (line 165-166) already does:
```typescript
getRecipe: (targetType: RecipeTargetType, targetId: string) =>
  apiFetch<unknown>(withQuery('/admin/menu/recipes', { targetType, targetId })),
```
Retype this from `unknown` to `RecipeResponse` — do not add a second binding.

**New `sessionsApi` module** — follow the exact same object-literal + `knownGaps`-adjacent section-comment-block convention used to separate `Menu`/`Tables`/`Inventory`/`Payments`/`Kitchen`/`Orders` (the `// --- Section --- ` banner comments at lines 52-54, 170-172, 308-310, 411-413, 450-452, 483-485):
```typescript
// ---------------------------------------------------------------------------
// Auth sessions
// ---------------------------------------------------------------------------
export type AuthSessionResponse = {
  sessionId: string; createdAt: string; expiresAt: string; lastUsedAt: string
  ipAddress: string | null; userAgent: string | null
}
export const sessionsApi = {
  list: () => apiFetch<AuthSessionResponse[]>('/auth/sessions'),
  revoke: (sessionId: string) => apiFetch<void>(`/auth/sessions/${sessionId}`, { method: 'DELETE' }),
  revokeOthers: () =>
    apiFetch<void>('/auth/sessions/revoke-others', { method: 'POST', body: JSON.stringify({ refreshToken: authState.refreshToken }) }),
}
```
Note: `sessionsApi.revokeOthers` needs `authState` imported from `../stores/auth` — no existing module in `modules.ts` currently imports `authState` (they only import `apiFetch` from `./client`), so this is a new import line, not a new pattern (matches how `client.ts` itself imports `authState`).

**`knownGaps` registry (lines 18-50):** do not add new entries for this phase's covered gaps (recipe/topping/sessions are now wired) — only the existing `menu`/`tables`/`payments`/`orders` entries stay, matching CONTEXT.md's "extend, don't mock" directive for what's still missing.

---

### `admin-ui/src/stores/auth.ts` (store, transform+CRUD) — EXTEND

**Analog:** itself, full file read (60 lines)

**Current shape to extend, not replace:**
```typescript
// Source: admin-ui/src/stores/auth.ts lines 12-17, 34-47
export const authState = reactive({
  accessToken: '',
  refreshToken: '',
  tokenType: 'Bearer',
  message: '',
})

export function setSession(response: AuthResponse) {
  authState.accessToken = response.accessToken
  authState.refreshToken = response.refreshToken
  authState.tokenType = response.tokenType || 'Bearer'
  authState.message = ''
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ ... }))
}
```

**Additive pattern** (per RESEARCH.md Pattern 3, verified against `JwtProvider.java`/`RoleEnum.java`) — add `roles: [] as string[]` to the `authState` reactive object, a `decodeRoles()` pure function above it, and one line inside both `setSession()` and `restoreSession()` (the exact two lifecycle points that already persist the token pair, lines 34 and 19-32):
```typescript
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
export const isAdmin = computed(() => authState.roles.includes('ADMIN'))
```
Do not change `AuthSession`/`setSession()`'s function signature — additive fields only (existing `auth.test.ts` asserts exact `authState.accessToken`/`tokenType`/`message` values and must keep passing unmodified).

---

### `admin-ui/src/stores/auth.test.ts` (test, unit) — EXTEND

**Analog:** itself, full file (85 lines) — `describe('setSession', ...)` / `describe('clearSession', ...)` / `describe('restoreSession', ...)` blocks, each with `beforeEach(() => { localStorage.clear(); clearSession() })` at the top.

**Pattern to copy for new `describe('decodeRoles' / 'isAdmin', ...)` blocks:** construct a fake JWT string with a base64url-encoded `{"roles":[...]}` payload (two `.`-joined segments are enough — signature isn't verified), call `setSession({ ...SAMPLE_AUTH_RESPONSE, accessToken: fakeJwt })`, then assert `authState.roles` / `isAdmin.value`. Match the existing `SAMPLE_AUTH_RESPONSE` object literal style (lines 4-10) for constructing fixtures.

---

### `admin-ui/src/router/index.ts` (route/config, request-response) — EXTEND

**Analog:** itself, full file (43 lines)

**Existing guard to extend** (lines 34-42) — add one more `if` branch, do not restructure the guard:
```typescript
router.beforeEach((to) => {
  if (to.meta.requiresAuth && !isAuthenticated()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && isAuthenticated()) {
    return { name: 'overview' }
  }
  return true
})
```
Add: `if (to.meta.adminOnly && !authState.roles.includes('ADMIN')) { return { name: 'menu' } }` — new import of `authState` from `../stores/auth` alongside the existing `isAuthenticated` import (line 3).

**New child routes** — follow the existing flat `children: [...]` array shape under the single `AdminLayout` parent route (lines 21-29); each entry is `{ path, name, component }` with lazy or static import matching the existing static-import style (all six current views are statically imported, lines 4-11) — keep RecipeView/SessionsView statically imported too, for consistency (no code-splitting precedent in this router).

```typescript
{ path: 'menu/dishes/:dishId/recipe', name: 'recipe', component: RecipeView, meta: { adminOnly: true } },
{ path: 'sessions', name: 'sessions', component: SessionsView },
```

---

### `admin-ui/src/router/index.test.ts` (test, unit/router) — EXTEND

**Analog:** itself, full file (54 lines) — `router.push(...)` + `expect(router.currentRoute.value.name).toBe(...)` assertion style, `setSession(...)`/`clearSession()` fixtures identical to `auth.test.ts`.

**Pattern for the new `adminOnly` redirect test:** call `setSession({...})` with an `accessToken` that decodes to `roles: ['STAFF']` (not `ADMIN`), `router.push('/menu/dishes/some-id/recipe')`, assert `router.currentRoute.value.name === 'menu'` (per RESEARCH.md's specified redirect target and UI-SPEC §6).

---

### `admin-ui/src/layouts/AdminLayout.vue` (nav shell) — EXTEND

**Analog:** itself, full file (81 lines)

**`navItems` array to extend** (lines 23-31) — flat array of `{ to, label, icon }`, rendered via `RouterLink` + `<component :is="item.icon">` (lines 52-57). Add one entry using a lucide icon not already imported (research suggests `ShieldCheck`):
```typescript
const navItems = [
  { to: '/', label: 'Overview', icon: Home },
  ...
  { to: '/orders', label: 'Orders', icon: ClipboardList },
  { to: '/sessions', label: 'Sessions', icon: ShieldCheck }, // ADD
]
```
No role gating on this nav item (Sessions is open to all authenticated roles per UI-SPEC §6/§4).

---

### `admin-ui/src/views/MenuView.vue` (component/view, CRUD) — EXTEND

**Analog:** itself (existing create-only category/dish modal code, full file 404 lines) + `TablesView.vue` for the chip text-vs-×-click distinction (there is no existing chip-text-click-to-edit precedent in `MenuView.vue` itself — categories only have `requestArchive` wired to the × today, line 245-248)

**Existing create pattern to extend into create+edit** (lines 112-143, category shown; dish is symmetric at 147-193):
```typescript
// Source: admin-ui/src/views/MenuView.vue lines 112-143 (create-only, to become create+edit)
const categoryModalOpen = ref(false)
const categoryForm = reactive({ name: '', description: '', sortOrder: 0, status: 'ACTIVE' as MenuStatus })
const categorySaving = ref(false)
const categoryFormError = ref('')

function openCategoryModal() {
  categoryForm.name = ''
  categoryForm.description = ''
  categoryForm.sortOrder = 0
  categoryForm.status = 'ACTIVE'
  categoryFormError.value = ''
  categoryModalOpen.value = true
}

async function submitCategory() {
  categorySaving.value = true
  categoryFormError.value = ''
  try {
    await menuApi.createCategory({ ... })
    categoryModalOpen.value = false
    await loadMenu()
  } catch (caught) {
    categoryFormError.value = caught instanceof ApiError ? caught.message : 'We could not save this category.'
  } finally {
    categorySaving.value = false
  }
}
```
Per RESEARCH.md Pattern 1: add `editingCategoryId = ref<string | null>(null)`, make `openCategoryModal(row?: CategoryResponse)` seed from `row` when present, and branch `submitCategory()` on `editingCategoryId.value` to call `updateCategory` vs `createCategory`. Modal `:title` becomes `computed(() => editingCategoryId.value ? 'Edit category' : 'New category')`.

**Chip click-vs-archive distinction** — the category chip template today (lines 243-250) wires the whole chip's × to `requestArchive`; per UI-SPEC §1, clicking the chip **text** must open edit mode while the × stays archive-only:
```html
<!-- Source: admin-ui/src/views/MenuView.vue lines 243-248, existing -->
<span v-for="category in menu.categories" :key="category.id" class="tag-chip">
  {{ category.name }}
  <button type="button" :aria-label="`Archive ${category.name}`" @click="requestArchive('category', category.id, category.name)">
    <X :size="12" />
  </button>
</span>
```
Wrap the text node in a clickable span/button calling `openCategoryModal(category)`, keep the existing `<button>` for archive unchanged.

**Error-handling convention:** category uses the older `caught instanceof ApiError ? caught.message : fallback` inline (lines 138-139); dish/archive use the newer `messageOf(caught, fallback)` helper (lines 189, 218) from `lib/format.ts`. **Use `messageOf` for all new/edited code** — it is the dominant, more recent convention (imported already at line 19).

**Topping management section** — REPLACES the placeholder `.notice-panel` (lines 305-314) that currently reads: *"Create/update endpoints for topping groups, topping options, and dish recipes are wired through `menuApi`, but a dedicated editor UI is deferred..."* — this exact block must be deleted, not left alongside the new UI. Closest structural analog for "list + row-level create + nested chip-list with archive" is a combination of:
- The existing category-chip archive pattern (lines 242-250) for the topping-option chips (`.tag-list`/`.tag-chip`, × → `archiveToppingOption`)
- `PaymentsView.vue`'s toolbar-button-opens-modal pattern (lines 89-97, `openPaymentModal`) for "New group"/"New option" buttons opening `Modal` instances

**Recipe entry point:** add a `ghost-button small` "Recipe" to the dish row's `.table-actions` cell (line 276-282, after the existing Archive button per UI-SPEC §2), navigating via `router.push({ name: 'recipe', params: { dishId: row.id } })` — no existing router-navigation-from-a-table-action precedent in this view; `router.push` usage itself is established in `AdminLayout.vue`'s `signOut()` (line 41).

**Role gating (`v-if="isAdmin"`)** applies to: "New category"/"New dish" buttons (lines 240, 261), category-chip text-click + × (lines 243-248), dish row Edit/Archive/Recipe buttons (lines 276-283), all new topping create/archive controls. Import `isAdmin` (standalone computed; not a field of authState) from `../stores/auth` (new import, following the existing relative-import convention at lines 11-19).

---

### `admin-ui/src/views/TablesView.vue` (component/view, CRUD + event-driven) — EXTEND

**Analog:** itself, full file (656 lines)

**Area/table create→edit extension:** identical mechanical pattern to Menu's category/dish (`openAreaModal`/`submitArea` lines 146-166, `openTableModal`/`submitTable` lines 192-226) — add `editingAreaId`/`editingTableId`, seed-on-row-arg, branch submit on `updateArea`/`updateTable`.

**Reservation status control — Pitfall 1 fix.** Current hardcoded implementation to REPLACE ENTIRELY:
```typescript
// Source: admin-ui/src/views/TablesView.vue lines 325-334 (to be deleted, not extended)
async function cancelReservation(reservationId: string) {
  actionError.value = ''
  try {
    const updated = await tablesApi.updateReservationStatus(reservationId, { status: 'CANCELLED' })
    reservationsCreated.value = reservationsCreated.value.map((reservation) =>
      reservation.reservationId === reservationId ? updated : reservation,
    )
  } catch (caught) {
    actionError.value = messageOf(caught, 'We could not cancel this reservation.')
  }
}
```
**Closest analog for the replacement multi-target-status modal is `sessionModal` (lines 66-137)** — a single reactive object with a `mode`/discriminant field, a computed title switching on that field, and one shared `Modal` + `<form>` with conditional fields (`v-if="sessionModal.mode === ..."`). The reservation-status modal is *simpler* (no mode-switching needed, just a status `<select>`), but the "one reactive form object + one shared Modal + computed title" shape is the pattern to reuse:
```typescript
// Source: admin-ui/src/views/TablesView.vue lines 68-93, 126-137 (sessionModal shape to imitate)
const sessionModal = reactive({ open: false, mode: 'open' as SessionMode, ... })
function openSessionModal(mode: SessionMode, row: TableOccupancyResponse) { ... }
const sessionModalTitle = computed(() => { switch (sessionModal.mode) { ... } })
```
New reservation-status modal state: `reactive({ open: false, reservationId: '', currentStatus: '' as ReservationStatus, nextStatus: 'CONFIRMED' as ReservationStatus, note: '', saving: false, error: '' })`. Submit escalates to `ConfirmDialog` for `CANCELLED`/`NO_SHOW` (reuse the existing `ConfirmDialog` usage pattern at lines 645-654 verbatim, new `danger` copy per UI-SPEC's Copywriting Contract), calls `tablesApi.updateReservationStatus` directly for `CONFIRMED`/`COMPLETED`.

**Row button rename:** "Cancel" (line 481-488) becomes "Status", `@click` changes from `cancelReservation(...)` to opening the new modal.

**Role gating:** `v-if="isAdmin"` on "New area"/area-× (lines 365, 370), "New table" (line 378), table row Edit/Archive (lines 445-451). Do NOT gate the Reservations panel, Occupancy panel, or session-modal actions (ADMIN+STAFF per UI-SPEC §6).

---

### `admin-ui/src/views/InventoryView.vue` (component/view, CRUD) — EXTEND

**Analog:** itself, full file (426 lines)

**Ingredient create→edit extension:** identical mechanical pattern to Menu/Tables (`openIngredientModal`/`submitIngredient`, lines 88-114) — add `editingIngredientId`, branch on `updateIngredient` vs `createIngredient`.

**Cost-history view (Open Question 1 in RESEARCH.md — no UI-SPEC-specified layout):** closest analog is the existing `openCostModal` add-cost flow (lines 210-220) for the trigger-button-next-to-row shape, combined with `DataTable`'s read-only cell-slot usage as seen in `PaymentsView.vue`'s payment-history table (columns without form inputs, just `{{ formatMoney(...) }}`/`{{ formatDateTime(...) }}` interpolation, lines 217-231). Recommended: a small "View costs" `ghost-button small` next to the existing "Add cost" button (line 285) opening a read-only `Modal` with a `DataTable` of `inventoryApi.listCosts(ingredientId)` results (columns: unit cost, cost unit, effective at, source, note, created at) — no form, no submit handler, just a fetch-on-open + `DataTable` render, following `PaymentsView`'s read-only-column convention.

**No role gating needed** — Inventory stays visible to ADMIN+STAFF per UI-SPEC §6 (do NOT wrap any Inventory control in `v-if="isAdmin"`).

---

### `admin-ui/src/views/RecipeView.vue` (NEW view, CRUD+transform) — NO DIRECT ANALOG for core mechanic

**Chrome/page-heading analog:** `MenuView.vue`/`InventoryView.vue`'s `page-heading`/`eyebrow`/`panel` structure (e.g. `MenuView.vue` lines 225-233):
```html
<section class="page-section">
  <div class="page-heading">
    <div>
      <p class="eyebrow">Admin module</p>
      <h2>Menu</h2>
      <p>Catalog, recipes, toppings, and costing reads.</p>
    </div>
  </div>
```

**Loading/error/empty-state analog:** the identical three-way conditional used in every existing panel (`MenuView.vue` lines 272-274, `InventoryView.vue` lines 274-276, `PaymentsView.vue` lines 214-216):
```html
<EmptyState v-if="error" tone="error" :body="error" @retry="loadMenu" />
<div v-else-if="loading" class="skeleton-table" />
<DataTable v-else ... />
```

**Ingredient-select-with-auto-fill-unit sub-pattern analog** — `InventoryView.vue`'s movement form (lines 169-174, 348):
```typescript
// Source: admin-ui/src/views/InventoryView.vue lines 169-174
function onMovementIngredientChange() {
  const selected = stock.value.find((row) => row.ingredientId === movementForm.ingredientId)
  if (selected) {
    movementForm.unit = selected.baseUnit
  }
}
```
```html
<!-- Source: admin-ui/src/views/InventoryView.vue line 348 -->
<select v-model="movementForm.ingredientId" required @change="onMovementIngredientChange">
```
This is the pattern each recipe row's ingredient `<select>` should reuse (per-row, indexed into the `rows` array instead of a single `movementForm`).

**GAP — no analog for the repeatable-row array mechanic itself.** Every existing form in `admin-ui/src/views/*.vue` is a single flat `reactive({...})` object bound 1:1 to one set of inputs (category form, dish form, area form, table form, ingredient form, movement form, cost form, payment form, refund form, reservation form — 9 forms surveyed, zero use a `ref<T[]>([])` + `v-for` + push/splice pattern). RESEARCH.md's Pattern 2 (`rows = ref<RecipeRowForm[]>([])`, `addRow()`/`removeRow(index)`) is genuinely net-new to this codebase — the planner/implementer should treat this as first-of-its-kind, not "extend an existing thing." Use RESEARCH.md's Pattern 2 code block directly (already vetted against the `RecipeRequest.Line` Java record's dual `ingredientId`+`ingredient` field requirement — see Pitfall 2).

**Row-level visual token analog** — UI-SPEC §2 specifies reusing the `.tag-chip` token set (`padding: 8px 10px; border: 1px solid var(--border); border-radius: 6px`) for each ingredient row's container, confirmed present in `style.css` (`.tag-chip` class, referenced at line 637 in the earlier grep). Do not invent a new radius/padding value.

**Route guard:** ADMIN-only, wired at the router level (`meta: { adminOnly: true }` — see router/index.ts section above), not just a component-level check.

---

### `admin-ui/src/views/SessionsView.vue` (NEW view, CRUD) — role-match analog

**Analog:** `admin-ui/src/views/PaymentsView.vue`, full file (297 lines) — closest existing "simple list + toolbar bulk action + row action" shape among the six existing views.

**Load-on-mount + loading/error state analog** (lines 12-49):
```typescript
// Source: admin-ui/src/views/PaymentsView.vue lines 12-47 (adapt: drop pagination/filters, sessions has none)
const loading = ref(true)
const error = ref('')
const items = ref<PaymentResponse[]>([])
...
async function loadPayments(reset = true) {
  loading.value = true
  error.value = ''
  try {
    const response = await paymentsApi.listPayments({ ... })
    items.value = response.items
  } catch (caught) {
    error.value = messageOf(caught, 'We could not load this data.')
  } finally {
    loading.value = false
  }
}
onMounted(() => loadPayments())
```
For Sessions this simplifies to a flat `sessionsApi.list()` call with no cursor/filter params — closer to `InventoryView.vue`'s plain `loadStock()`/`loadMovements()` (lines 28-38) than Payments' cursor pagination. Use whichever is simpler at implementation time; both are valid same-codebase analogs.

**Toolbar bulk-action analog** (`PaymentsView.vue` `Toolbar` usage, lines 171-205, `actions` slot with a single button) — for "Sign out other sessions":
```html
<Toolbar>
  <template #actions>
    <button class="ghost-button" type="button" @click="confirmRevokeOthersOpen = true">Sign out other sessions</button>
  </template>
</Toolbar>
```
Visible/enabled only when `items.value.length > 1` (a new conditional not present in any existing analog — simple `v-if`).

**Row-level revoke action analog** — `PaymentsView.vue`'s row-level "Record refund" button opening a modal (lines 226-230) is structurally closest, but Sessions' row action opens a `ConfirmDialog` directly (no intermediate form), which is closer to `MenuView.vue`'s `requestArchive` → `ConfirmDialog` flow (lines 200-222, 392-401):
```typescript
// Source: admin-ui/src/views/MenuView.vue lines 197-222 (adapt: archive -> revoke)
const confirmTarget = ref<{ kind: 'category' | 'dish'; id: string; name: string } | null>(null)
const confirmPending = ref(false)
function requestArchive(kind, id, name) { confirmTarget.value = { kind, id, name } }
async function confirmArchive() {
  confirmPending.value = true
  try {
    await menuApi.archiveCategory(confirmTarget.value.id) // -> sessionsApi.revoke(sessionId)
    confirmTarget.value = null
    await loadMenu() // -> reload session list
  } catch (caught) {
    error.value = messageOf(caught, 'We could not archive this record.')
  } finally {
    confirmPending.value = false
  }
}
```
```html
<ConfirmDialog
  :open="Boolean(confirmTarget)"
  title="Confirm action"
  :message="...appropriate copy from Copywriting Contract..."
  confirm-label="Archive"  <!-- -> 'Revoke' -->
  danger
  :pending="confirmPending"
  @close="confirmTarget = null"
  @confirm="confirmArchive"  <!-- -> confirmRevoke -->
/>
```

**No role gating** — visible to all authenticated roles per UI-SPEC §6/§4.

**Known limitation to respect (Pitfall 5):** `AuthSessionResponse` has no current-session flag — do not add a "this device" badge; all rows render identically via `DataTable`'s default `{{ display(value) }}` cell (auto-escaped, no `v-html`).

---

### `admin-ui/src/lib/recipe.ts` (NEW, optional utility) — role-match

**Analog:** `admin-ui/src/lib/format.ts`, full file (43 lines) — small, pure, individually-exported functions with no side effects, each independently unit-testable:
```typescript
// Source: admin-ui/src/lib/format.ts lines 40-42 (shape to imitate)
export function messageOf(caught: unknown, fallback: string): string {
  return caught instanceof Error && caught.message ? caught.message : fallback
}
```
Recommended per RESEARCH.md's Wave 0 Gaps: extract `toRecipeRequest(dishId, dishName, rows, ingredients)` as a pure function here so the `ingredientId`→`ingredient` (name) resolution (Pitfall 2) is unit-testable without mounting `RecipeView.vue` — this codebase has zero precedent for testing Vue view internals directly (confirmed: only `api/client.test.ts`, `stores/auth.test.ts`, `router/index.test.ts` exist), so a `lib/` extraction is the lowest-friction way to get RECIPE-01 test coverage.

---

## Shared Patterns

### Loading / Error / Empty state (universal, all new panels)
**Source:** every existing view (`MenuView.vue` lines 272-274 shown as canonical instance)
**Apply to:** Recipe cost panel, Sessions table, Inventory cost-history modal
```html
<EmptyState v-if="error" tone="error" :body="error" @retry="loadX" />
<div v-else-if="loading" class="skeleton-table" />
<DataTable v-else :columns="..." :rows="..." row-key="..." empty-text="..." />
```

### Error message extraction
**Source:** `admin-ui/src/lib/format.ts` lines 40-42, imported everywhere except the two oldest inline sites in `MenuView.vue` (lines 138-139, 189)
**Apply to:** every new catch block
```typescript
} catch (caught) {
  formError.value = messageOf(caught, 'We could not save this X.')
}
```

### Modal-driven form (create+edit and single-action forms alike)
**Source:** `admin-ui/src/components/Modal.vue` (46 lines, `open`/`title` props, `close` emit, Escape-key + backdrop-click handling built in) — used unmodified everywhere
**Apply to:** all 5 D-01 shared edit modals, topping group/option create modals, reservation-status modal
```html
<Modal :open="xModalOpen" :title="xModalTitle" @close="xModalOpen = false">
  <form class="field-grid" @submit.prevent="submitX">
    ...
    <p v-if="xFormError" class="form-error span-2">{{ xFormError }}</p>
    <div class="modal-footer span-2">
      <button class="ghost-button" type="button" @click="xModalOpen = false">Cancel</button>
      <button class="primary-button" type="submit" :disabled="xSaving">{{ xSaving ? 'Saving…' : 'Save changes' }}</button>
    </div>
  </form>
</Modal>
```

### Destructive confirmation
**Source:** `admin-ui/src/components/ConfirmDialog.vue` (32 lines) — used identically in `MenuView.vue` (lines 392-401), `TablesView.vue` (lines 645-654), `OrdersView.vue` (lines 175-195)
**Apply to:** revoke-session, revoke-others, reservation CANCELLED/NO_SHOW transitions, topping-option archive
```html
<ConfirmDialog :open="Boolean(target)" title="..." :message="..." confirm-label="..." danger :pending="pending" @close="target = null" @confirm="confirmAction" />
```

### Role-gated visibility (new this phase)
**Source:** RESEARCH.md Pattern 3 + UI-SPEC §6 role matrix (authoritative)
**Apply to:** `MenuView.vue` (all catalog CRUD + topping + Recipe-button), `TablesView.vue` (area/table CRUD only — NOT reservations/occupancy), router `adminOnly` meta on the Recipe route
```html
<button v-if="isAdmin" class="ghost-button" type="button" @click="openXModal">New X</button>
```
Never use `disabled` + tooltip for this — `v-if` only (locked decision, D-01 discretion notes + UI-SPEC §6).

### Typed API-module convention
**Source:** `admin-ui/src/api/modules.ts` — every module (`menuApi`, `tablesApi`, `inventoryApi`, `paymentsApi`, `kitchenApi`, `ordersApi`) is a flat exported object of arrow functions over `apiFetch<T>`
**Apply to:** all new `update*` bindings, `sessionsApi`
```typescript
updateX: (id: string, body: XRequest) => apiFetch<XResponse>(`/path/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
```

---

## No Analog Found

| File/Feature | Role | Data Flow | Reason |
|---|---|---|---|
| Recipe builder repeatable ingredient-row array (`rows.value.push/splice`, `v-for` over a reactive array of form-row objects) | component sub-pattern | transform (nested rows → flat DTO) | Every existing form in the 9 surveyed views (Menu category/dish, Tables area/table/reservation, Inventory ingredient/movement/cost, Payments payment/refund) is a single flat `reactive({...})` object — none use a `ref<T[]>([])` + indexed `v-for` + add/remove-row mechanic. RESEARCH.md Pattern 2 is the reference implementation to follow (already verified against the Java `RecipeRequest.Line` DTO shape); planner should treat this as net-new, not "extend existing," and budget accordingly (e.g., its own task/wave rather than folding into the D-01 mechanical edit-modal work). |
| Ingredient cost-history display location | component sub-pattern | CRUD (read-only list) | UI-SPEC §1-6 does not specify an exact widget for `listCosts()` (flagged as RESEARCH.md Open Question 1) — recommended shape given above ("View costs" button + read-only Modal+DataTable) is a reasonable synthesis of existing patterns, not a literal analog; confirm with planner before implementation if scope is ambiguous. |

## Metadata

**Analog search scope:** `admin-ui/src/**` only (views, components, stores, router, api, lib) — backend Java source consulted only via RESEARCH.md's already-verified DTO/endpoint shapes, not re-read here.
**Files scanned:** 21 (`api/auth.ts`, `api/client.ts`, `api/client.test.ts`, `api/modules.ts`, `App.vue`, all 8 `components/*.vue`, `layouts/AdminLayout.vue`, `lib/format.ts`, `main.ts`, `router/index.ts`, `router/index.test.ts`, `stores/auth.ts`, `stores/auth.test.ts`, `style.css` (grepped), all 8 `views/*.vue`)
**Convention derivation:** ran `node bin/gsd-tools.cjs verify conventions --derive --scope admin-ui/src` (shared module also used by `gsd-code-reviewer`) — see `## Conventions` below.
**Pattern extraction date:** 2026-07-15

---

## Conventions

Derived via `gsd-tools.cjs verify conventions --derive --scope admin-ui/src` (majority-vote + entropy over the `admin-ui/src` subtree).

| Axis | Dominant | Share | Entropy | Status |
|---|---|---|---|---|
| File-name casing | camelCase | 70% (7/10) | 0.881 | **named contract** |
| Identifier casing | camelCase | 94.1% (16/17) | 0.323 | **named contract** |
| Export style | ESM (`export`) | n/a (6/6, single-variant sample) | — | insufficient-data (all-ESM, too small a sample to score entropy — treat as named contract in practice: this is a Vite/ESM-only frontend, no CJS anywhere) |
| Import style | ESM (`import`) | 100% (9/9) | 0.0 | **named contract** |

All four axes clear the ≥70% dominance bar for a **named contract** in `admin-ui/src` — new files (RecipeView.vue, SessionsView.vue, lib/recipe.ts, api/modules.test.ts) should use camelCase file names (e.g. `recipe.ts`, not `Recipe.ts`; Vue SFCs are the sole PascalCase exception already established — `RecipeView.vue`/`SessionsView.vue` match the existing `MenuView.vue`/`TablesView.vue` PascalCase-for-components convention, which the tool's "other" bucket for file-name casing captures), camelCase identifiers, and ESM `import`/`export` throughout — no `require`/`module.exports` anywhere in this subtree.

**Contested hotspots (author's choice):** none within `admin-ui/src` itself — this subtree is internally consistent on all four axes. The one genuinely contested split in this repository is the top-level **CJS↔SDK dual resolver**: `bin/lib/**` is CJS (`module.exports`/`require`) while `sdk/src/**` is ESM (`export`/`import`) — each half is internally consistent per-directory, contested only when compared repo-wide. `admin-ui/src` is unambiguously on the ESM side of that split (matching `sdk/src/**`'s convention, not `bin/lib/**`'s) — reviewers/planners for this phase should hold every new file to the ESM contract above and not be swayed by the CJS half of the repo, since that half is a different subtree's local convention, not a global default.
