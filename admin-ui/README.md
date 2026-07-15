# feat1 Admin UI

Vue 3 + Vite + TypeScript admin interface for the feat1 Spring Boot backend. Covers ADMIN/STAFF
operations across menu, tables, inventory, payments, kitchen, and order cancellation.

## Requirements

- Node.js 20+ and npm.
- A running instance of the backend (see [Backend Expectations](#backend-expectations)).
- An ADMIN or STAFF account on that backend.

## Setup

```bash
npm install
npm run dev
```

`npm run dev` starts the Vite dev server (default `http://localhost:5173`) with hot reload.

## Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Base URL the app calls for every backend request (login, refresh, and all `/admin/**` endpoints). |

Set it inline when the backend is not on the default port/host:

```bash
VITE_API_BASE_URL=http://localhost:8080 npm run dev
```

Or create `admin-ui/.env.local` (gitignored) with:

```
VITE_API_BASE_URL=http://localhost:8080
```

## Backend Expectations

This app is a pure client of the existing Spring Boot backend in the repository root — it does not
run its own server-side logic. Before using it:

1. Start the backend (Postgres, Redis, and Kafka dependencies must also be up — see the repository
   root `docker-compose.yml`/`README` for the full stack).
2. Confirm the backend is reachable at the URL configured in `VITE_API_BASE_URL`.
3. Have an ADMIN or STAFF user's credentials ready (registered via the backend's `/auth/register`
   flow, or seeded dev data if available).

The app authenticates via `POST /auth/login`, attaches `Authorization: Bearer <accessToken>` to
every subsequent request, and retries a request exactly once via `POST /auth/refresh` on a `401`
before returning the user to `/login` with a session-expired message.

## Verification

```bash
npm run build   # vue-tsc typecheck + vite production build
npm run test    # vitest unit/component tests
```

Both commands must exit 0 before a change to `admin-ui/` is considered complete. Neither command
requires the backend to be running — they check the frontend in isolation.

## Manual Smoke Path

Run this after `npm run build` and `npm run test` pass, against a live backend:

1. Start the Spring Boot backend and its dependencies (Postgres/Redis/Kafka).
2. Start this app: `npm run dev`.
3. Open the printed Vite URL — confirm it redirects to `/login` when logged out.
4. Sign in with an ADMIN or STAFF account. Confirm you land on the Overview page (not `/login`).
5. Visit each sidebar module in turn — Overview, Menu, Tables, Inventory, Payments, Kitchen,
   Orders — and confirm each either renders backend-backed data or a clear, non-crashing
   API/session error (never a blank page or unhandled console exception).
6. Exercise one write path per module that has one, watching for a success state and a
   destructive-confirmation dialog where applicable:
   - **Menu:** create a category, then archive it.
   - **Tables:** open a table session, then close it.
   - **Inventory:** record a stock movement (e.g. a receipt) for an existing ingredient.
   - **Payments:** record a payment against a known order ID.
   - **Kitchen:** advance one active ticket item to its next status.
   - **Orders:** cancel a line or order by ID (use an ID surfaced from the Kitchen board).
7. Sign out (or let a refresh token expire) and confirm protected routes redirect back to
   `/login` with the session-expired message.

## Known Backend Gaps

These are backend capability gaps, not frontend bugs — each is surfaced in-UI via a `GapNotice`
rather than hidden or faked:

- **Admin menu category/dish listing** — no `GET` list endpoint exists; the Menu module reads the
  public active-menu tree (`GET /menus/public`) instead, so inactive/archived items will not
  appear there.
- **Reservation listing** — no `GET` list endpoint exists; reservations created during the current
  session are shown in a session-local table until the page reloads.
- **Payment history filters** — `GET /admin/payments` currently supports only `orderId`,
  `orderUserId`, `cursor`, and `size`. Status/method/date-range filter controls are visible but
  disabled pending backlog item 999.1.
- **Global admin order listing** — no endpoint exists to list/search all orders; the Orders module
  is action-only (cancel by order/line ID), with a convenience panel of IDs pulled from the
  Kitchen board.
- **Overview metrics** — no backend aggregate/summary endpoint exists yet; the Overview page is
  scoped to navigation only until one is added.

See `.planning/phases/19-vuejs-admin-management-interface/19-VERIFICATION.md` for the latest
recorded build/test verification results and full gap tracking.
