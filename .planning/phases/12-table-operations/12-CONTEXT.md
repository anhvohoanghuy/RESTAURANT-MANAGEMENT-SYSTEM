---
phase: 12-table-operations
status: planned
created: 2026-07-06
requirements: [TABLE-008, TABLE-009, TABLE-010, TABLE-011, TABLE-012, TABLE-013, TABLE-014, TABLE-015, TABLE-016]
---

# Phase 12 Context: Table Operations

## Goal

Add operational table management on top of the existing Table Context catalog. This phase covers table sessions, occupancy state, and reservations while preserving the current DDD boundary: Table Context owns table operational state, and Order Context owns cart/order logic.

## Initial Scope

- Table sessions:
  - Staff/admin can open, close, and cancel a session for a dining table.
  - A table can have at most one active session.
  - Session statuses should support at least `OPEN`, `CLOSED`, and `CANCELLED`.
- Occupancy:
  - Track or derive table occupancy as `AVAILABLE`, `OCCUPIED`, `RESERVED`, `CLEANING`, or `OUT_OF_SERVICE`.
  - Opening a session should make the table occupied.
  - Closing a session should release the table to an agreed next state.
- Reservations:
  - Staff/admin can create reservations with customer contact, party size, time window, and status.
  - Active reservation windows cannot overlap for the same table.
  - Reservation statuses should support `PENDING`, `CONFIRMED`, `SEATED`, `CANCELLED`, `NO_SHOW`, and `COMPLETED`.
- Availability:
  - Expose availability reads by time window and party size.
- Order integration:
  - Cart/Order can optionally reference a table session through a port.
  - Do not move cart/order ownership into Table Context.
- Events:
  - Publish table session, occupancy, and reservation events after commit.
  - Do not add Kafka consumers in this phase.

## Deferred

- Floor-plan UI.
- Waitlist management.
- Multi-branch restaurant scoping.
- External reservation provider integration.
- Advanced table merge/split workflows.

## Discussion Needed

- Should reservations be staff-only in this phase, or should public users be able to create reservations?
- Should occupancy be persisted as a table state, derived from active sessions/reservations, or both?
- When a table session closes, should the default next state be `AVAILABLE` or `CLEANING`?
- Should orders require an active table session, or should table sessions remain optional for backward compatibility?

## Planning Assumptions

- Reservations are staff/admin managed in Phase 12; public users can only read availability.
- Occupancy has persisted manual state, while effective occupancy prioritizes active sessions and active reservations.
- Closing a session defaults to `AVAILABLE`, with `CLEANING` available as an explicit close target.
- Orders and carts keep backward compatibility: `tableSessionId` is optional, not required.
