# Roadmap: feat1

## Overview

The backend first delivered a Restaurant Menu Context for sellable catalog management. The next milestone work stabilizes the existing auth and identity code into a minimal local Auth Context so admin/user protected backend operations have a tested security foundation.

## Phases

- [x] **Phase 01: menu-context** - Add restaurant menu catalog CRUD and public read API. Completed: 2026-06-10
- [ ] **Phase 02: auth-context-mvp** - Stabilize local registration, login, JWT access, refresh-token lifecycle, logout revocation, and role-protected route access.
- [x] **Phase 03: google-oauth-2-login** - Add Google OAuth 2 ID-token login that issues the existing backend token pair. (completed 2026-07-04)
- [x] **Phase 04: email-verification-password-reset** - Add backend token APIs for email verification and local password reset without SMTP/provider integration. Completed: 2026-07-04
- [x] **Phase 06: auth-hardening** - Add rate limits, local login lockout, auth audit logging, and self-service refresh-session management. Completed: 2026-07-05
- [x] **Phase 07: menu-order-validation** - Add service-only menu selection validation and price snapshot quotes for future Order/Cart flows. Completed: 2026-07-05
- [x] **Phase 08: table-context** - Add dining area/table catalog, active public table listing, table validation snapshot service, and minimal dev seed data. (completed 2026-07-05)

## Phase Details

### Phase 01: menu-context
**Goal**: Add a backend Restaurant Menu Context that models categories, dishes, topping groups, topping options, and recipes; provides admin CRUD for catalog management; and exposes a public menu tree containing only active sellable data.
**Depends on**: Nothing (first phase)
**Requirements**: [MENU-001, MENU-002, MENU-003, MENU-004, MENU-005]
**Success Criteria** (what must be TRUE):
  1. Admin users can manage categories, dishes, topping groups, topping options, and recipes under `/admin/menu/**`.
  2. Public clients can call `GET /menus/public` to retrieve the active catalog.
  3. Public responses are category -> dish -> topping group -> topping option trees and exclude inactive or archived sellable data.
  4. Recipes can be stored for dishes and topping options, but recipes are not exposed by the public menu response.
  5. Focused tests cover lifecycle filtering, topping selection validation, recipe line validation, and public response shape.
**Plans**: 1 plan

Plans:
- [x] 01-01: Implement Restaurant Menu Context vertical slice

### Phase 02: auth-context-mvp
**Goal**: Turn the existing auth and identity scaffolding into a tested local authentication MVP with registration, login, JWT access tokens, refresh-token persistence, logout revocation, and role-based protection for existing backend routes.
**Depends on**: Phase 1
**Requirements**: [AUTH-001, AUTH-002, AUTH-003, AUTH-004, AUTH-005, AUTH-006, AUTH-007, AUTH-008, AUTH-009, AUTH-010]
**Success Criteria** (what must be TRUE):
  1. Public users can register locally through a controller endpoint that creates user, credential, and default role records atomically.
  2. Local login validates username/password and returns a usable access token plus refresh token without exposing password hashes.
  3. Refresh-token validation checks JWT validity, token type, stored token state, and expiry metadata before issuing a new token pair or access token according to the chosen policy.
  4. Logout revokes the submitted refresh token and reuse fails with a consistent auth error response.
  5. Authenticated users can call a self/profile endpoint and protected routes enforce `ADMIN`/`USER` access through Spring Security.
  6. Focused tests cover registration, login, refresh, logout, JWT filter behavior, and protected route authorization.
**Plans**: 1 plan

Plans:
- [ ] 02-01: Implement Auth Context MVP vertical slice

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 01. menu-context | 1/1 | Complete | 2026-06-10 |
| 02. auth-context-mvp | 0/1 | Planned | - |
| 03. google-oauth-2-login | 1/1 | Complete | 2026-07-04 |
| 04. email-verification-password-reset | 1/1 | Complete | 2026-07-04 |
| 06. auth-hardening | 1/1 | Complete | 2026-07-05 |
| 07. menu-order-validation | 1/1 | Complete | 2026-07-05 |
| 08. table-context | 1/1 | Complete    | 2026-07-05 |

### Phase 3: Google OAuth 2 login

**Goal:** Add Google OAuth 2 login through backend verification of Google ID tokens, preserving the existing internal JWT access/refresh-token lifecycle and local auth behavior.
**Requirements**: [AUTH-011]
**Depends on:** Phase 2
**Success Criteria** (what must be TRUE):
  1. Public clients can call `POST /auth/google` with `{ "idToken": "..." }`.
  2. The backend verifies Google ID token signature, issuer, expiry, configured audience, subject, email, and email verification before issuing internal tokens.
  3. Existing Google credentials log in the mapped user without storing Google access or refresh tokens.
  4. New verified Google users are auto-registered with `USER`.
  5. Existing local users are auto-linked only when Google is authoritative for the email (`gmail.com` or `hd` claim present).
  6. Focused tests cover create, login, auto-link, rejected non-authoritative link, controller mapping, and HTTP integration.
**Plans:** 1/1 plans complete

Plans:
- [x] 03-01: Implement Google OAuth 2 ID-token login

### Phase 04: email-verification-password-reset

**Goal:** Add backend-only email verification and password reset token flows that persist verification state, reset local credentials safely, and expose notification ports for future email delivery integration.
**Requirements**: [AUTH-012, AUTH-013, AUTH-014]
**Depends on:** Phase 2, Phase 3
**Success Criteria** (what must be TRUE):
  1. Public clients can request email verification and password reset without receiving raw tokens in API responses.
  2. Verification and reset tokens are stored as hashes, expire, and are single-use.
  3. Email verification marks users as verified while allowing unverified users to keep logging in.
  4. Password reset only changes local credentials and revokes active refresh tokens after success.
  5. Google-created or Google-linked verified emails mark the user email as verified.
  6. Focused unit, controller, and integration tests cover token issue, consume, expiry, verification, reset, and notification-port behavior.
**Plans:** 1/1 plans complete

Plans:
- [x] 04-01: Implement email verification and password reset token APIs

### Phase 06: auth-hardening

**Goal:** Harden the auth surface with Redis-backed rate limiting and local account lockout, persistent audit events, refresh-session metadata, and self-service session revocation APIs.
**Requirements**: [AUTH-015, AUTH-016, AUTH-017, AUTH-018]
**Depends on:** Phase 2, Phase 3, Phase 4
**Success Criteria** (what must be TRUE):
  1. Local login, Google OAuth, and recovery endpoints enforce the configured rate-limit buckets and return `RATE_LIMIT_EXCEEDED` when exceeded.
  2. Local accounts are locked for 15 minutes after 5 failed login attempts and return `ACCOUNT_LOCKED` while locked.
  3. Auth-sensitive events are persisted with type, outcome, user id when available, principal, IP, user agent, reason, and timestamp.
  4. Refresh-token records store session metadata and update last-used information during refresh.
  5. Authenticated users can list active sessions, revoke one owned session, and revoke all other sessions without exposing raw refresh-token values.
  6. Focused unit and integration tests cover limiter/lockout behavior, audit persistence, session ownership, and endpoint contracts.
**Plans:** 1/1 plans complete

Plans:
- [x] 06-01: Implement auth hardening

### Phase 07: menu-order-validation

**Goal:** Add a Menu Context application/domain service that validates order-bound dish selections from `dishId + List<toppingOptionId>` and returns immutable price snapshot quotes for future Order/Cart flows, without exposing a new public HTTP API in this phase.
**Requirements**: [MENU-006, MENU-007]
**Depends on:** Phase 1
**Success Criteria** (what must be TRUE):
  1. A service accepts `dishId` plus a list of selected topping option IDs and performs no HTTP/controller work in this phase.
  2. The service verifies the category and dish are active/orderable.
  3. The service verifies every selected topping option exists, is active, and belongs to a topping group under the selected dish.
  4. The service enforces every topping group's `minSelections` and `maxSelections`.
  5. Valid selections return a quote containing dish snapshot, selected topping snapshots, base price, additional topping price, and total price.
  6. Invalid selections throw stable menu domain errors such as `MENU_DISH_NOT_ORDERABLE`, `MENU_TOPPING_NOT_ORDERABLE`, `MENU_TOPPING_NOT_IN_DISH`, `MENU_TOPPING_GROUP_REQUIRED`, and `MENU_TOPPING_GROUP_LIMIT_EXCEEDED`.
  7. Focused tests cover valid quote calculation and each validation failure category.
**Plans:** 1/1 plans complete

Plans:
- [x] 07-01: Implement service-only menu order validation and quote snapshot

### Phase 08: table-context

**Goal:** Add a Table Context catalog for dining areas and dining tables with admin CRUD/archive, public active table listing, minimal dev seed data, and a service-only table validator for future Order Context flows.
**Requirements**: [TABLE-001, TABLE-002, TABLE-003, TABLE-004, TABLE-005, TABLE-006, TABLE-007]
**Depends on:** Auth/security baseline
**Success Criteria** (what must be TRUE):
  1. The backend models `DiningArea` and `DiningTable` as a Table Context catalog, without table sessions, occupancy, reservations, or branch/restaurant scoping in this phase.
  2. Admin users can create, update, list, fetch, and archive dining areas and dining tables under `/admin/tables/**`.
  3. Public clients can call `GET /tables/public` and receive an area -> active tables tree that excludes inactive or archived areas/tables.
  4. Dining table `code` is stable and unique for QR/display usage; UUID remains the internal identifier.
  5. Capacity is optional, but any provided capacity must be positive.
  6. A service-only validator returns a stable table snapshot for future Order Context and fails with `TABLE_NOT_ORDERABLE` or `TABLE_AREA_NOT_ORDERABLE`.
  7. Minimal dev/test seed data can create sample areas and tables safely without becoming required production fixture data.
**Plans:** 1/1 plans complete

Plans:
- [x] 08-01: Implement Table Context catalog, public listing, validator, and dev seed
