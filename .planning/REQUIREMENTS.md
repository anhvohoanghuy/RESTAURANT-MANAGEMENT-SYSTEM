# Requirements: feat1

**Defined:** 2026-06-10
**Core Value:** Restaurant operators can manage a sellable menu catalog, clients can read the active menu tree, and protected backend operations are guarded by a minimal local authentication context.

## v1 Requirements

### Restaurant Menu Context

- [x] **MENU-001**: The backend introduces a Restaurant Menu Context with domain objects for menu categories, dishes, topping groups, topping options, recipes, and recipe lines.
- [x] **MENU-002**: The persistence layer stores menu catalog data in relational tables compatible with Spring Data JPA and MySQL: `menu_categories`, `dishes`, `topping_groups`, `topping_options`, `recipes`, and `recipe_lines`.
- [x] **MENU-003**: The application exposes admin CRUD endpoints for catalog management, including lifecycle changes for categories, dishes, and topping options.
- [x] **MENU-004**: The application exposes a public/client read endpoint that returns only active categories, active dishes, active topping options, and their topping groups as a category -> dish -> topping tree.
- [x] **MENU-005**: Recipes are managed separately for dishes and topping options and are excluded from the public menu response.
- [x] **MENU-006**: The Menu Context provides an application/domain service that validates an order-bound dish selection from `dishId + List<toppingOptionId>` without exposing a new HTTP API in this phase.
- [x] **MENU-007**: Valid menu selections return a dish and topping price snapshot quote, while invalid selections fail with stable menu domain error codes for inactive/not-orderable data, wrong-dish toppings, and topping group min/max violations.

### Auth Context MVP

- [ ] **AUTH-001**: The backend exposes a public registration endpoint for local users that creates a user, credential, and default role in one transaction.
- [ ] **AUTH-002**: The backend supports local username/password login and returns an access token plus refresh token when credentials are valid.
- [ ] **AUTH-003**: Access tokens include user identity, roles, permissions, issued-at, expiration, token id, and token type claims.
- [ ] **AUTH-004**: Refresh tokens are persisted server-side with expiry metadata and are validated against both JWT claims and stored token state.
- [ ] **AUTH-005**: Refresh requests rotate or revoke refresh tokens according to a documented single-session or multi-session policy.
- [ ] **AUTH-006**: Logout revokes the active refresh token so it cannot be reused.
- [ ] **AUTH-007**: Authenticated users can call a self/profile endpoint to inspect their own identity and roles without exposing password hashes.
- [ ] **AUTH-008**: Role-protected routes enforce `ADMIN` and `USER` access consistently through Spring Security.
- [ ] **AUTH-009**: Auth failures return consistent HTTP statuses and response bodies for invalid credentials, expired tokens, revoked tokens, and unauthorized access.
- [ ] **AUTH-010**: Focused tests cover registration, login success/failure, refresh success/failure, logout revocation, JWT filter behavior, and protected route access.
- [x] **AUTH-011**: The backend supports Google OAuth 2 login by verifying Google ID tokens, issuing the existing backend access/refresh token pair, and creating or linking Google credentials according to the documented account-linking policy.
- [x] **AUTH-012**: The backend supports email verification through single-use backend tokens without exposing raw tokens in public API responses.
- [x] **AUTH-013**: The backend supports password reset for local credentials through single-use backend tokens and revokes active refresh tokens after reset.
- [x] **AUTH-014**: Email verification and password reset notifications are emitted through a backend notification port that can be faked in tests and replaced by SMTP/provider integration later.
- [x] **AUTH-015**: The backend rate-limits auth-sensitive endpoints and locks local accounts after repeated failed login attempts using Redis-backed TTL state.
- [x] **AUTH-016**: The backend persists auth security audit events for login, logout, refresh reuse, recovery, Google OAuth, and session revocation flows.
- [x] **AUTH-017**: The backend records refresh-session metadata including IP address, user agent, created time, last-used time, expiry, and revocation state.
- [x] **AUTH-018**: Authenticated users can list and revoke their own refresh sessions without exposing refresh-token values.

### Table Context

- [x] **TABLE-001**: The backend introduces a Table Context with catalog domain objects for `DiningArea` and `DiningTable`.
- [x] **TABLE-002**: The persistence layer stores dining area and dining table catalog data in relational tables compatible with Spring Data JPA and MySQL.
- [x] **TABLE-003**: The application exposes admin CRUD and archive endpoints for dining areas and dining tables under `/admin/tables/**`.
- [x] **TABLE-004**: The application exposes `GET /tables/public`, returning only active dining areas and active dining tables as an area -> table tree.
- [x] **TABLE-005**: Dining tables have a stable unique `code` for QR/display usage, while capacity remains optional but must be positive when present.
- [x] **TABLE-006**: The Table Context provides a service-only validator that returns a table snapshot for future Order Context flows and fails with stable error codes `TABLE_NOT_ORDERABLE` and `TABLE_AREA_NOT_ORDERABLE`.
- [x] **TABLE-007**: The application provides minimal dev/test seed data for sample dining areas and tables without requiring production fixture data.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Order/cart/payment | This phase is catalog management only. |
| Table sessions/occupancy/reservations | Phase 08 is table catalog only; operational table state is deferred. |
| Inventory costing | Recipe lines store ingredient, quantity, and unit only. |
| SKU/variant generation | Topping choices are modeled as topping groups and options. |
| Public recipe exposure | Recipes are admin/internal catalog data. |
| SMTP/provider email delivery | Phase 04 defines notification ports and token APIs only; real delivery provider integration is deferred. |
| Full role/permission admin UI | MVP only needs enough role enforcement to protect existing backend routes. |
| MFA/TOTP | Deferred beyond Phase 06; hardening focuses on rate limits, lockout, audit, and self-service sessions. |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| MENU-001 | Phase 1 | Complete |
| MENU-002 | Phase 1 | Complete |
| MENU-003 | Phase 1 | Complete |
| MENU-004 | Phase 1 | Complete |
| MENU-005 | Phase 1 | Complete |
| MENU-006 | Phase 7 | Complete |
| MENU-007 | Phase 7 | Complete |
| AUTH-001 | Phase 2 | Planned |
| AUTH-002 | Phase 2 | Planned |
| AUTH-003 | Phase 2 | Planned |
| AUTH-004 | Phase 2 | Planned |
| AUTH-005 | Phase 2 | Planned |
| AUTH-006 | Phase 2 | Planned |
| AUTH-007 | Phase 2 | Planned |
| AUTH-008 | Phase 2 | Planned |
| AUTH-009 | Phase 2 | Planned |
| AUTH-010 | Phase 2 | Planned |
| AUTH-011 | Phase 3 | Complete |
| AUTH-012 | Phase 4 | Complete |
| AUTH-013 | Phase 4 | Complete |
| AUTH-014 | Phase 4 | Complete |
| AUTH-015 | Phase 6 | Complete |
| AUTH-016 | Phase 6 | Complete |
| AUTH-017 | Phase 6 | Complete |
| AUTH-018 | Phase 6 | Complete |
| TABLE-001 | Phase 8 | Complete |
| TABLE-002 | Phase 8 | Complete |
| TABLE-003 | Phase 8 | Complete |
| TABLE-004 | Phase 8 | Complete |
| TABLE-005 | Phase 8 | Complete |
| TABLE-006 | Phase 8 | Complete |
| TABLE-007 | Phase 8 | Complete |

**Coverage:**
- v1 requirements: 32 total
- Mapped to phases: 32
- Unmapped: 0

---
*Requirements defined: 2026-06-10*
*Last updated: 2026-07-05 for Table Context*
