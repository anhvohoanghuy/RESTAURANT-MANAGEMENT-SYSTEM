# Roadmap: feat1

## Overview

The backend first delivered a Restaurant Menu Context for sellable catalog management. The next milestone work stabilizes the existing auth and identity code into a minimal local Auth Context so admin/user protected backend operations have a tested security foundation.

## Phases

- [x] **Phase 01: menu-context** - Add restaurant menu catalog CRUD and public read API. Completed: 2026-06-10
- [ ] **Phase 02: auth-context-mvp** - Stabilize local registration, login, JWT access, refresh-token lifecycle, logout revocation, and role-protected route access.

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
**Plans**: 0 plans

Plans:
- [ ] TBD (run `/gsd-plan-phase 2` to break down)

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 01. menu-context | 1/1 | Complete | 2026-06-10 |
| 02. auth-context-mvp | 0/0 | Planned | - |
