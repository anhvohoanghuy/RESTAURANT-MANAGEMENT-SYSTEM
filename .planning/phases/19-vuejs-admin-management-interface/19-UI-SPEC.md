---
phase: 19
slug: vuejs-admin-management-interface
status: approved
shadcn_initialized: false
preset: none
created: 2026-07-10
---

# Phase 19 - UI Design Contract

> Visual and interaction contract for the VueJS admin management interface.

---

## Product Surface

The first screen after authentication is the admin workbench, not a landing page. The interface is for repeated restaurant operations by ADMIN/STAFF users who need fast scanning, low ambiguity, and predictable controls.

Primary modules:

| Module | Core Jobs |
|--------|-----------|
| Overview | Show operational counts, low-stock alerts, open table sessions, payment activity, and kitchen queue health. |
| Menu | Manage categories, dishes, topping groups/options, recipes, and recipe costing reads. |
| Tables | Manage dining areas/tables, occupancy, table sessions, reservations, and availability. |
| Inventory | Manage ingredients, costs, stock balances, stock movements, and low-stock review. |
| Orders | Inspect submitted orders, cancel eligible orders/items, and surface payment/kitchen status. |
| Kitchen | Show kitchen board and advance item fulfillment status. |
| Payments | List payment history, record payments, record refunds, and expose backend filter gaps clearly. |

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none |
| Preset | not applicable |
| Component library | Vue-native custom components |
| Icon library | @lucide/vue |
| Font | system UI stack |

Use custom Vue components for shell, tables, forms, dialogs, tabs, filters, toasts, and empty/error states. Do not introduce a large UI kit in the first slice; this keeps the frontend small and avoids style drift.

---

## Layout Contract

| Area | Contract |
|------|----------|
| App shell | Fixed left sidebar on desktop, top bar with account/status controls, content area with constrained max width only for forms. |
| Sidebar | Icon + text navigation, active state, collapsed mobile drawer. |
| Page header | Title, short operational subtitle, primary action button, optional filter cluster. |
| Tables | Dense rows, sticky header where practical, actions aligned right, stable row heights. |
| Forms | Drawer or modal for create/edit, two-column on desktop, single-column on mobile. |
| Destructive actions | Confirmation dialog with exact entity name and irreversible/archival wording. |

No hero, no decorative cards inside cards, no marketing copy. Repeated entities may use compact cards only on narrow screens where table columns collapse poorly.

---

## Spacing Scale

Declared values must be multiples of 4:

| Token | Value | Usage |
|-------|-------|-------|
| xs | 4px | Icon gaps, dense metadata |
| sm | 8px | Button padding, table cell inner gaps |
| md | 16px | Default component spacing |
| lg | 24px | Page header and panel padding |
| xl | 32px | Major layout gaps |
| 2xl | 48px | Rare page separation |

Exceptions: none.

---

## Typography

| Role | Size | Weight | Line Height |
|------|------|--------|-------------|
| Body | 14px | 400 | 1.5 |
| Label | 12px | 600 | 1.3 |
| Heading | 20px | 650 | 1.25 |
| Section | 16px | 650 | 1.35 |
| Metric | 28px | 700 | 1.15 |

Use letter spacing `0`. Do not scale font size with viewport width. Keep table copy compact and action labels verb-first.

---

## Color

| Role | Value | Usage |
|------|-------|-------|
| Dominant (60%) | #f7f8fa | App background |
| Secondary (30%) | #ffffff | Panels, modals, table surfaces |
| Navigation | #172033 | Sidebar and top nav text surfaces |
| Accent (10%) | #0f766e | Primary action, active nav indicator, focused controls |
| Informational | #2563eb | Links and neutral status badges |
| Warning | #b45309 | Low-stock, nearing deadline, retryable states |
| Destructive | #dc2626 | Archive, cancel, refund confirmation warnings |
| Border | #d8dee8 | Table grid, inputs, panel boundaries |
| Muted text | #667085 | Secondary metadata |

Accent reserved for: primary submit buttons, active navigation, focus ring, selected tabs. Do not use accent on every clickable element.

---

## Interaction Contract

| Pattern | Contract |
|---------|----------|
| Auth guard | Unauthenticated users land on `/login`; expired tokens trigger refresh once, then return to login with a session-expired message. |
| API errors | Show concise inline error near the failed surface; preserve form input. |
| Loading | Use skeleton rows for tables and disabled submit states for forms. |
| Empty states | State what is missing and provide the next action when the user has permission. |
| Filters | Use inputs/selects/date range controls in a single toolbar; keep current filters reflected in URL query where useful. |
| Optimistic updates | Avoid for financial, inventory, and cancellation actions; wait for server confirmation. |
| Keyboard | Dialogs trap focus; Escape closes non-destructive dialogs; Enter submits forms when safe. |

---

## Copywriting Contract

| Element | Copy |
|---------|------|
| Primary CTA | Save changes |
| Create CTA | New item |
| Empty state heading | Nothing to review yet |
| Empty state body | When records are available, they will appear here. Adjust filters or create a new record. |
| Error state | We could not load this data. Retry, or check your session and API connection. |
| Destructive confirmation | Confirm action: this may affect active restaurant operations. |
| Session expired | Your session expired. Sign in again to continue. |

Entity-specific CTAs should replace generic wording: `New dish`, `Record payment`, `Open session`, `Record movement`, `Advance item`.

---

## Data And API Contract

| Concern | Contract |
|---------|----------|
| API base URL | `VITE_API_BASE_URL`, defaulting to `http://localhost:8080`. |
| Auth | Store access token for Authorization Bearer requests; refresh token is retained only as long as needed for refresh/logout flow. |
| API client | One shared fetch wrapper handles JSON, auth header, refresh retry, typed errors, and logout on repeated 401. |
| Backend gaps | Missing list/detail endpoints or filters must be shown as disabled UI states and recorded as follow-up GSD items. |
| Swagger | OpenAPI docs at `/v3/api-docs` may inform client typing later, but Phase 19 does not require codegen. |

Known backend gap to surface: `GET /admin/payments` currently supports `orderId`, `orderUserId`, `cursor`, and `size`; status/method/date filters are deferred in Phase 999.1.

---

## Responsive Contract

| Viewport | Contract |
|----------|----------|
| Desktop >= 1024px | Sidebar visible, tables use full columns, drawers max 560px. |
| Tablet 768-1023px | Sidebar collapsible, filter toolbars wrap, high-priority table columns remain. |
| Mobile < 768px | Sidebar becomes drawer, tables collapse to stacked row cards, primary action remains in header. |

Text must not overflow buttons/cards/table cells; long IDs use monospace and middle truncation.

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | none | not required |
| third-party | none | not required |

---

## Checker Sign-Off

- [x] Dimension 1 Copywriting: PASS
- [x] Dimension 2 Visuals: PASS
- [x] Dimension 3 Color: PASS
- [x] Dimension 4 Typography: PASS
- [x] Dimension 5 Spacing: PASS
- [x] Dimension 6 Registry Safety: PASS

**Approval:** approved 2026-07-10
