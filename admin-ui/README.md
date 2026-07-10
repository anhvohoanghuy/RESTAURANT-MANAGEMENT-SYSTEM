# feat1 Admin UI

Vue 3 + Vite admin interface for the feat1 Spring Boot backend.

## Setup

```bash
npm install
npm run dev
```

Set the backend URL when it is not running on `http://localhost:8080`:

```bash
VITE_API_BASE_URL=http://localhost:8080 npm run dev
```

## Verification

```bash
npm run test
npm run build
```

## Smoke Path

1. Start the Spring Boot backend.
2. Start this app with `npm run dev`.
3. Open the Vite URL and confirm `/login` renders.
4. Sign in with an ADMIN or STAFF account.
5. Open Overview, Menu, Tables, Inventory, Payments, Kitchen, and Orders from the sidebar.
6. Confirm backend-backed pages either load data or show a clear API/session error.

## Known Gaps

- Payment history status/method/date filters need backend support from Phase 999.1.
- Global admin order listing is not exposed by the backend yet.
- Overview metrics need a backend aggregate endpoint.
