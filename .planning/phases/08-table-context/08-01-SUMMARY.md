---
phase: 08-table-context
plan: 08-01
subsystem: table_context
tags: [backend, ddd, table-catalog, tests]
status: complete
completed: 2026-07-05
key-files:
  created:
    - src/main/java/com/example/feat1/DDD/table_context/domain/model/DiningArea.java
    - src/main/java/com/example/feat1/DDD/table_context/domain/model/DiningTable.java
    - src/main/java/com/example/feat1/DDD/table_context/application/TableCatalogService.java
    - src/main/java/com/example/feat1/DDD/table_context/application/TableSeedInitializer.java
    - src/main/java/com/example/feat1/DDD/table_context/infrastructure/presentation/AdminTableController.java
    - src/main/java/com/example/feat1/DDD/table_context/infrastructure/presentation/PublicTableController.java
    - src/test/java/com/example/feat1/DDD/table_context/application/TableCatalogServiceTest.java
    - src/test/java/com/example/feat1/DDD/table_context/integration/TableContextIntegrationTest.java
  modified:
    - src/main/java/com/example/feat1/DDD/auth/infrastructure/security/SecurityConfig.java
    - src/main/resources/application.properties
    - src/test/resources/application.properties
metrics:
  tests: 78
  failures: 0
  errors: 0
---

# Plan 08-01 Summary: Table Context Catalog, Public Listing, Validator, And Dev Seed

## What Changed

- Added a new `DDD/table_context` package with domain models for `DiningArea`, `DiningTable`, `TableStatus`, and stable `TableDomainException` codes.
- Added JPA entities, Spring Data repositories, domain repository adapters, and mapper support for `dining_areas` and `dining_tables`.
- Added `TableCatalogService` with admin CRUD/archive, public active area -> table tree assembly, and `validateOrderableTable(tableId)` snapshot validation.
- Added admin APIs under `/admin/tables/**` and public `GET /tables/public`.
- Updated security to permit anonymous `GET /tables/public` while keeping `/admin/**` protected.
- Added conditional minimal seed data guarded by `table.seed.enabled`; production default is false and test profile enables it.
- Added focused unit, controller, and integration tests for table catalog behavior.

## Commits

| Commit | Description |
|--------|-------------|
| Uncommitted | Implemented in the current workspace without staging/committing because the worktree already contains prior phase changes. |

## Deviations

- No Order/Cart code was introduced.
- No table session, occupancy, reservation, booking, or QR generation feature was introduced.
- Admin list endpoints return catalog records ordered by sort order/code or name; archived records remain visible to admin callers because archive is a catalog lifecycle state, not physical deletion.

## Verification

- Ran full Maven test suite:
  - Command: `$env:JAVA_HOME='C:\Users\chinh\.jdks\ms-17.0.19'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd test`
  - Result: `Tests run: 78, Failures: 0, Errors: 0, Skipped: 0`

## Self-Check: PASSED

- Phase 08 introduces Table Context, not Order Context.
- Admin CRUD/archive exists for dining areas and dining tables.
- Public active listing returns area -> active tables.
- Table `code` is unique and stable.
- Capacity is optional but positive if provided.
- Service-only validator returns table snapshots and stable errors.
- Minimal dev/test seed data exists and is environment-safe.
