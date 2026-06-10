# Roadmap: feat1

## Overview

Add a Menu Context to the existing Spring Boot authentication and identity backend so clients can request a permission-aware navigation tree for the current authenticated user.

## Phases

- [ ] **Phase 01: menu-context** - Add permission-aware backend menu tree API.

## Phase Details

### Phase 01: menu-context
**Goal**: Add a backend Menu Context that models navigational menu items, associates them with existing permissions, and exposes an authenticated endpoint that returns only the menu entries available to the current user.
**Depends on**: Nothing (first phase)
**Requirements**: [MENU-001, MENU-002, MENU-003, MENU-004, MENU-005]
**Success Criteria** (what must be TRUE):
  1. Authenticated users can call `GET /menus/me`.
  2. The response contains a deterministic menu tree.
  3. The response excludes inactive and unauthorized menu entries.
  4. Focused tests cover filtering, tree assembly, and endpoint authorization.
**Plans**: 1 plan

Plans:
- [ ] 01-01: Implement Menu Context vertical slice

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 01. menu-context | 0/1 | Planned | - |
