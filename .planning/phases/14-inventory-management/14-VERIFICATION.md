---
phase: 14-inventory-management
status: passed
verified: 2026-07-07
score: 8/8 success criteria verified
has_blocking_gaps: false
---

# Phase 14 Verification: Inventory Management

**Phase Goal:** Extend Inventory Context from costing into operational stock management with stock-on-hand balances, inventory movement records, manual receipts/adjustments/waste, and low-stock visibility while keeping automatic order deduction as an explicit later integration decision.

**Verified:** 2026-07-07
**Overall Verdict:** PASS
**Method:** Goal-backward inspection of implemented code under `src/main/java/com/example/feat1/DDD/inventory_context/` and tests under `src/test/`, plus focused test execution. Not derived from SUMMARY.md claims.

## Commands

- `./mvnw -o test -Dtest=InventoryStockServiceTest` → `Tests run: 8, Failures: 0, Errors: 0` / BUILD SUCCESS
- Full suite (run by orchestrator prior to verification): `./mvnw clean test` → 119 tests, 0 failures, 0 errors, BUILD SUCCESS
- `git diff --name-only 1e8e4ca~1 003ed17 -- src/main | grep -v inventory_context` → empty (no source touched outside Inventory Context)

## Success Criteria (ROADMAP Contract)

| # | Criterion | Verdict | Evidence |
|---|-----------|---------|----------|
| 1 | Persist stock balances per ingredient for a default location | PASS | `InventoryStockBalanceEntity` (`inventory_stock_balances`, `quantityOnHand` base-unit, `DEFAULT_LOCATION="DEFAULT"`, unique constraint on `ingredient_id`+`location_code`); `InventoryStockBalanceRepository.findByIngredient_IdAndLocationCode` |
| 2 | Staff/admin record movements for receipts, adjustments, waste, corrections | PASS | `InventoryMovementType` {RECEIPT, ADJUSTMENT_IN, ADJUSTMENT_OUT, WASTE, STOCK_COUNT}; `InventoryStockService.recordMovement`; `POST /admin/inventory/movements`; integration test `staffCanRecordMovementsAndInspectStockAndLowStock` |
| 3 | Movement records are immutable audit facts (qty, unit, reason, reference, actor, timestamp) | PASS | `InventoryStockMovementEntity` persists quantity, unit, baseQuantityDelta, baseUnit, resultingBalance, note, referenceType, referenceId, actorId, createdAt; no update path exists — corrections are new rows (STOCK_COUNT/ADJUSTMENT). Signed delta + resultingBalance reconstruct history |
| 4 | Stock-on-hand reads expose current quantity per ingredient | PASS | `getStock`, `listStock` → `StockBalanceResponse.quantityOnHand`; `GET /admin/inventory/stock`, `GET /admin/inventory/ingredients/{id}/stock`; integration asserts `quantityOnHand=50.0` |
| 5 | Outbound movements cannot drive stock negative unless explicit adjustment path | PASS | `recordMovement`: `type.isOutbound()` (WASTE, ADJUSTMENT_OUT) throws `STOCK_INSUFFICIENT` when `current < baseQuantity`; only STOCK_COUNT bypasses the guard as the explicit correction path. Unit test `wasteWithInsufficientStockIsRejectedAndBalanceUntouched` + `stockCountSetsBalanceToCountedQuantityEvenBelowCurrent`; integration `insufficientOutboundMovementIsRejected` → `INVENTORY_STOCK_INSUFFICIENT` |
| 6 | Low-stock thresholds definable; staff/admin can list low-stock ingredients | PASS | `lowStockThreshold` column on balance; `InventoryStockBalanceRepository.findLowStock` (qty ≤ threshold); `listLowStock`; `GET /admin/inventory/low-stock`; unit test `lowStockThresholdMarksBalanceAsLow`; integration low-stock assertion |
| 7 | Public menu, order, payment, recipe costing contracts backward compatible | PASS | `git diff` shows zero main-source changes outside `inventory_context`; SecurityConfig unmodified (already authorizes `/admin/inventory/**`). Costing refactor is internal: `InventoryCostingService` delegates to shared `UnitConverter` with identical conversion factors (kg/g, l/ml) and `piece(s)→pcs` normalization. Full 119-test suite green incl. `InventoryCostingServiceTest`, `InventoryCostingIntegrationTest`, menu/order/payment suites |
| 8 | Focused tests cover validation, balance updates, unit conversion, low-stock reads, authorization | PASS | `InventoryStockServiceTest` (8 unit: receipt+conversion, insufficient-stock, adjustment-out, stock-count, non-positive qty, archived ingredient, low-stock flag, stock-not-found); `InventoryStockIntegrationTest` (3: staff flow, insufficient rejection, USER forbidden / anonymous unauthorized) |

**Score: 8/8 PASS**

## Requirement Coverage (INV-012 .. INV-021)

| Requirement | Verdict | Evidence |
|-------------|---------|----------|
| INV-012 stock-on-hand balances, default location | PASS | `InventoryStockBalanceEntity`, `DEFAULT_LOCATION` |
| INV-013 immutable movements (receipt/adjustment/waste/correction/count) | PASS | `InventoryStockMovementEntity` (no mutators applied post-create), all 5 `InventoryMovementType` values |
| INV-014 inbound receipts with qty, unit, optional source/reference, note | PASS | `StockMovementRequest` {quantity, unit, referenceType, referenceId, note}; RECEIPT/ADJUSTMENT_IN inbound handling |
| INV-015 outbound waste/adjustments with reason + validation | PASS | WASTE, ADJUSTMENT_OUT with active-ingredient, positive-qty, conversion, non-negative validation; `note` carries reason; movement type categorizes reason |
| INV-016 quantities use ingredient units + supported conversions consistent with costing | PASS | Shared `UnitConverter` used by both `InventoryStockService` and `InventoryCostingService` (same factors) |
| INV-017 outbound cannot go negative except explicit adjustment/correction | PASS | Outbound guard + STOCK_COUNT correction path; tests confirm |
| INV-018 low-stock thresholds + list | PASS | `lowStockThreshold`, `findLowStock`, `listLowStock`, low-stock endpoint |
| INV-019 reads expose current balance, latest movement time, low-stock state; no public menu cost data | PASS | `StockBalanceResponse` {quantityOnHand, lastMovementAt, lowStock}; no cost fields; endpoints are admin/staff-only under `/admin/inventory/**` |
| INV-020 stable errors: invalid movement, invalid qty, missing/inactive ingredient, unsupported conversion, insufficient stock | PASS | `InventoryDomainException`: MOVEMENT_INVALID (null type/ingredient), MOVEMENT_QUANTITY_INVALID, INGREDIENT_NOT_FOUND, INGREDIENT_NOT_ACTIVE, UNIT_CONVERSION_UNSUPPORTED, STOCK_INSUFFICIENT, STOCK_NOT_FOUND. Note: a malformed enum string is a framework 400 (Jackson) rather than a domain code; null type is covered by MOVEMENT_INVALID |
| INV-021 focused tests: validation, balance updates, conversion, low-stock, authorization, costing backward compat | PASS | 8 unit + 3 integration inventory tests; costing regression via passing `InventoryCosting*` suites in the full run |

## Key Wiring Verified

| From | To | Via | Status |
|------|----|-----|--------|
| `InventoryStockController` | `InventoryStockService` | constructor injection, all 5 endpoints delegate | WIRED |
| `recordMovement` | balance + movement persistence | single `@Transactional`: `balanceRepository.save` then `movementRepository.save`; guard throws before any save (unit test asserts `never()` save on rejection) | WIRED (atomic) |
| `/admin/inventory/**` | ADMIN/STAFF authorization | `SecurityConfig` lines 63-65 `.hasAnyRole("ADMIN","STAFF")` | WIRED (USER→403, anon→401 in integration test) |
| Controller | actor metadata | `@AuthenticationPrincipal CustomUserDetails` → `principal.getId()` → `movement.actorId` | WIRED |
| Costing + Stock | conversion parity | both call `UnitConverter.convert/normalizeUnit` | WIRED |

## Atomicity & Immutability

- Atomicity: `recordMovement` is `@Transactional`; the insufficient-stock guard throws before either `save`, so a failed movement never partially updates the balance (unit test `wasteWithInsufficientStockIsRejectedAndBalanceUntouched` verifies both repositories receive `never()` save).
- Immutability: `InventoryStockMovementEntity` rows are only ever created; no service path updates an existing movement. Downward corrections are modeled as new STOCK_COUNT/ADJUSTMENT_OUT rows carrying a signed `baseQuantityDelta` and `resultingBalance`.

## Backward Compatibility

- No main-source file outside `inventory_context` changed across the six phase commits.
- `SecurityConfig` unchanged (documented in SUMMARY deviation #1; verified — the `/admin/inventory/**` rule pre-existed).
- `InventoryCostingService` refactor only extracts normalization/conversion into `UnitConverter` with identical factors and rounding (scale 6, HALF_UP); costing tests pass unchanged.
- Public menu, order, payment contexts untouched; full suite (119 tests) green.

## Anti-Pattern Scan

No blocking anti-patterns. No TODO/FIXME/XXX/HACK/PLACEHOLDER markers in the created files. No stub returns (`return []`/`return null` for dynamic data); reads flow from real repository queries.

## Residual Notes (non-blocking)

- INV-020: an invalid movement-type *string* surfaces as a framework 400 (Jackson enum bind error) rather than a domain `INVENTORY_*` code; the null-type case is covered by MOVEMENT_INVALID. Acceptable for this phase; a dedicated code could be added later if a stable payload is required.
- Integration test does not assert `actorId` propagation (SUMMARY deviation #2) because MockMvc injects a Spring `User`, not `CustomUserDetails`; actor resolution is exercised by the real JWT filter in production. Unit path passes an actor id through `recordMovement`.
- Deferred per CONTEXT.md and correctly absent: automatic recipe-based order deduction, Kafka consumers, purchase orders/suppliers, multi-location, FIFO/weighted-average valuation.

---
_Verified: 2026-07-07_
_Verifier: Claude (gsd-verifier)_
