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
- [ ] **AUTH-011**: The backend supports Google OAuth 2 login by verifying Google ID tokens, issuing the existing backend access/refresh token pair, and creating or linking Google credentials according to the documented account-linking policy.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Order/cart/payment | This phase is catalog management only. |
| Inventory costing | Recipe lines store ingredient, quantity, and unit only. |
| SKU/variant generation | Topping choices are modeled as topping groups and options. |
| Public recipe exposure | Recipes are admin/internal catalog data. |
| Password reset/email verification | Requires email delivery and account recovery policy, planned after local auth MVP. |
| Full role/permission admin UI | MVP only needs enough role enforcement to protect existing backend routes. |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| MENU-001 | Phase 1 | Complete |
| MENU-002 | Phase 1 | Complete |
| MENU-003 | Phase 1 | Complete |
| MENU-004 | Phase 1 | Complete |
| MENU-005 | Phase 1 | Complete |
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
| AUTH-011 | Phase 3 | Planned |

**Coverage:**
- v1 requirements: 16 total
- Mapped to phases: 16
- Unmapped: 0

---
*Requirements defined: 2026-06-10*
*Last updated: 2026-07-04 for Auth Context MVP*
