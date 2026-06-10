# Roadmap: feat1

## Overview

Replace the previous permission-navigation menu direction with a Restaurant Menu Context for a sales catalog. The backend manages menu categories, dishes, topping groups, topping options, and recipes, and exposes a public read API for currently sellable menu data.

## Phases

- [x] **Phase 01: menu-context** - Add restaurant menu catalog CRUD and public read API. Completed: 2026-06-10

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

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 01. menu-context | 1/1 | Complete | 2026-06-10 |
