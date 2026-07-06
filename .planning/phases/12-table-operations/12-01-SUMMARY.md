# Phase 12 Plan 12-01 Summary: Table Operations

**Completed:** 2026-07-06
**Status:** Complete

## Implemented

- Added operational table session persistence and APIs for staff/admin open, close, and cancel flows.
- Added occupancy state tracking with `AVAILABLE`, `OCCUPIED`, `RESERVED`, `CLEANING`, and `OUT_OF_SERVICE`.
- Added reservation persistence and APIs for create, confirm, seat, cancel/no-show/complete transitions, and overlap prevention.
- Added staff/admin and public availability reads by time window and party size.
- Added table-operation Kafka event publishing after transaction commit.
- Added optional Order/Cart table-session linkage through an Order Context port and Table Context adapter.
- Extended cart/order responses and `OrderCreated` event payloads with `tableSessionId`.
- Standardized `TableDomainException` as an app-level exception so table domain errors return stable `400` responses outside Table Context controllers.

## Verification

- Added integration tests for session open/close, occupancy, public availability exclusion, reservation overlap/status/seat flow, and table operation event publishing.
- Updated order submission integration to submit a cart with `tableSessionId` and assert the submitted order/event preserves it.
- Ran full Maven test suite successfully.
