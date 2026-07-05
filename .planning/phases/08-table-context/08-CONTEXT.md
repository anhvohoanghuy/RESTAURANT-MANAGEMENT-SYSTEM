---
phase: 08-table-context
created: 2026-07-05
status: ready_for_planning
requirements: [TABLE-001, TABLE-002, TABLE-003, TABLE-004, TABLE-005, TABLE-006, TABLE-007]
---

# Phase 08 Context: Table Context

<domain>
## Phase Boundary

Add a backend Table Context catalog so restaurant operators can manage dining areas and dining tables, public clients can read active tables, and a future Order Context can validate that a cart/order is attached to an orderable table.

In scope:
- Model `DiningArea` and `DiningTable` as catalog data.
- Admin CRUD/archive for areas and tables.
- Public active listing as area -> tables.
- Stable unique table `code` for QR/display usage.
- Optional positive table capacity.
- Service-only table validator that returns a snapshot for future Order Context.
- Minimal dev/test seed data for sample areas/tables.

Out of scope:
- No table sessions, occupancy, reservations, or booking.
- No operational table status such as `OCCUPIED` or `RESERVED`.
- No branch/restaurant context.
- No Order/Cart aggregate.
- No QR code generation endpoint.
- No production-required fixture data.
</domain>

<decisions>
## Locked Decisions

### Scope
- **D-01:** Phase 08 is Table Catalog MVP only. It does not model live table usage.

### Model
- **D-02:** Use `DiningArea` plus `DiningTable`.
- **D-03:** `DiningArea` fields include name, sort order, and catalog status.
- **D-04:** `DiningTable` fields include code, name, optional capacity, sort order, catalog status, and area relation.
- **D-05:** Do not introduce Restaurant/Branch scoping in this phase.

### Lifecycle
- **D-06:** Use catalog statuses matching Menu Context style: `ACTIVE`, `INACTIVE`, and `ARCHIVED`.
- **D-07:** Archive means excluded from admin active workflows and public listing; physical deletion is not required.

### API
- **D-08:** Admin APIs live under `/admin/tables/areas` and `/admin/tables`.
- **D-09:** Public API is `GET /tables/public`, returning active areas and active tables only.

### Identity And Validation
- **D-10:** UUID remains the internal identifier.
- **D-11:** `code` is stable, unique, and intended for QR/display usage such as `A01` or `VIP-02`.
- **D-12:** Capacity is optional, but when present must be a positive integer.
- **D-13:** The future Order Context consumes a service-only validator, not a public table validation endpoint.
- **D-14:** `validateOrderableTable(tableId)` returns snapshot fields: `tableId`, `code`, `name`, `areaId`, and `areaName`.
- **D-15:** Missing/inactive/archived table fails with `TABLE_NOT_ORDERABLE`.
- **D-16:** Missing/inactive/archived area fails with `TABLE_AREA_NOT_ORDERABLE`.

### Seed Policy
- **D-17:** Add minimal dev/test seed data only, with a few sample areas and tables.
- **D-18:** Seed data must be safe for non-production usage and must not become required production fixture data.
</decisions>

<code_context>
## Existing Project Context

- Menu Context already establishes DDD-ish package layout under `src/main/java/com/example/feat1/DDD/menu_context`.
- Menu Context uses catalog lifecycle filtering for public active responses; Table Context should mirror that mental model.
- Auth/security hardening already protects `/admin/**` routes and leaves public read endpoints available according to security config conventions.
- Phase 07 added service-only validation inside Menu Context for future Order/Cart. Phase 08 should provide a similar service-only validator for tables.
</code_context>

<canonical_refs>
## Canonical References

- `.planning/phases/01-menu-context/01-CONTEXT.md` - original catalog context boundary and lifecycle decisions.
- `.planning/phases/07-menu-order-validation/07-CONTEXT.md` - service-only validation pattern for future Order/Cart.
- `src/main/java/com/example/feat1/DDD/menu_context/application/MenuCatalogService.java` - application service and public tree assembly pattern.
- `src/main/java/com/example/feat1/DDD/menu_context/domain/model/MenuStatus.java` - catalog status style to mirror if present.
- `src/main/java/com/example/feat1/config/SecurityConfig.java` - admin/public route protection conventions.
</canonical_refs>

<phase_09_handoff>
## Future Order Context Handoff

Phase 09 is expected to be `order-cart-mvp`, placed in Order Context. It should depend on this phase through a port that validates table orderability and stores the returned table snapshot in cart/order state. Order Context should not own Table Context catalog rules.
</phase_09_handoff>
