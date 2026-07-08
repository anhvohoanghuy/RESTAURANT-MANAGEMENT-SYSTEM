---
phase: 17-kitchen-context
plan: 06
subsystem: api
tags: [spring-boot, spring-security, mockmvc, jpa, kitchen-context]

# Dependency graph
requires:
  - phase: 17-kitchen-context (plan 02)
    provides: KitchenTicketItemRepository.findByStatusNot, KitchenTicketEntity/KitchenTicketItemEntity, KitchenItemStatus
  - phase: 17-kitchen-context (plan 05)
    provides: KitchenTicketAdvanceService.advance(orderId, itemId, request, actorId) -> KitchenItemResponse, AdvanceItemStatusRequest, KitchenDomainException
provides:
  - KitchenBoardItemResponse DTO + KitchenBoardService.board() read-only active-item query
  - KitchenController exposing PATCH /admin/orders/{orderId}/items/{itemId}/status and GET /admin/orders/kitchen-board
  - KitchenIntegrationTest proving RBAC, happy-path advance, stable illegal-transition error, and board filter end-to-end on H2
affects: [kitchen-context, order-context, future kitchen-board UI]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Read-only board query: @Transactional(readOnly = true) service method streaming a repository query into a flat DTO list (mirrors OrderSubmissionService.listOrders)"
    - "Existing-route reuse: new controller endpoints under an already-secured path prefix require no new SecurityConfig entry"
    - "Integration test principal construction: build a CustomUserDetails + UsernamePasswordAuthenticationToken via .with(authentication(...)) instead of the default MockMvc user() builder, whenever the controller reads @AuthenticationPrincipal CustomUserDetails"

key-files:
  created:
    - src/main/java/com/example/feat1/DDD/kitchen_context/application/dto/KitchenBoardItemResponse.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/application/KitchenBoardService.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/presentation/KitchenController.java
    - src/test/java/com/example/feat1/DDD/kitchen_context/integration/KitchenIntegrationTest.java
  modified: []

key-decisions:
  - "Board sort order: ticket createdAt then item status ordinal, for a stable staff-facing queue order (plan left this optional; implemented for determinism)"
  - "Integration test uses a hand-built CustomUserDetails + UsernamePasswordAuthenticationToken (via authentication(...)) for the STAFF-role case instead of the plan's suggested .with(user(...).roles(...)) helper, because KitchenController reads @AuthenticationPrincipal CustomUserDetails and Spring Security Test's user() builder does not produce that principal type; USER-role/anonymous denial assertions still use the plain user()/no-auth builder since those requests never reach the controller cast"
  - "Publisher ports (KitchenSettleTriggerPublisher, KitchenTicketStatusChangedPublisher) are @MockitoBean-replaced in the integration test, mirroring the existing OrderSubmissionIntegrationTest/TableOperationIntegrationTest convention, so no real Kafka broker call happens during the advance PATCH test"

requirements-completed: [D-05]

# Metrics
duration: 6min
completed: 2026-07-08
---

# Phase 17 Plan 06: Kitchen Staff Endpoints Summary

**PATCH single-item advance and GET active-board endpoints under the existing `/admin/orders/**` RBAC, with a MockMvc integration test proving RBAC, the happy-path advance, the stable illegal-transition error, and the board filter end-to-end on H2**

## Performance

- **Duration:** 6 min
- **Started:** 2026-07-08T15:29:25+07:00 (plan reset commit)
- **Completed:** 2026-07-08T15:34:52+07:00 (full suite green)
- **Tasks:** 2
- **Files modified:** 4 (all new)

## Accomplishments
- Kitchen board read model (`KitchenBoardItemResponse` + `KitchenBoardService.board()`) queries `findByStatusNot(COMPLETED)` and returns a stable-ordered DTO list.
- `KitchenController` exposes the two staff endpoints of D-05 (`PATCH .../items/{itemId}/status`, `GET /admin/orders/kitchen-board`) under the already-secured `/admin/orders/**` route, with zero changes to `SecurityConfig.java`.
- `KitchenIntegrationTest` (`@SpringBootTest` + `MockMvc`, H2) seeds a `KitchenTicket`/`KitchenTicketItem` directly via the repositories and asserts: STAFF advance QUEUED->PREPARING returns 200 + `PREPARING`; an illegal skip (PREPARING->SERVED) returns stable `400 KITCHEN_TRANSITION_INVALID`; the board lists only non-COMPLETED items; USER-role and anonymous callers are denied (403/401) on both endpoints.

## Task Commits

Each task was committed atomically:

1. **Task 1: Kitchen board DTO + read-only KitchenBoardService** - `402ba21` (feat)
2. **Task 2: KitchenController (PATCH advance + GET board) with MockMvc integration test** - `833f71d` (feat)

**Plan metadata:** (this summary's commit, added by the orchestrator)

## Files Created/Modified
- `src/main/java/com/example/feat1/DDD/kitchen_context/application/dto/KitchenBoardItemResponse.java` - Board row DTO (itemId, orderId, orderLineId, dishName, quantity, status)
- `src/main/java/com/example/feat1/DDD/kitchen_context/application/KitchenBoardService.java` - `@Transactional(readOnly = true) board()` querying `findByStatusNot(COMPLETED)`, sorted by ticket createdAt then status
- `src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/presentation/KitchenController.java` - PATCH advance + GET board endpoints, no new security annotation
- `src/test/java/com/example/feat1/DDD/kitchen_context/integration/KitchenIntegrationTest.java` - End-to-end RBAC + transition + board coverage

## Decisions Made
- Board query sorted by `ticket.createdAt` then `status` ordinal for a deterministic staff queue view (plan marked this "optional"; implemented since test assertions and future UI benefit from determinism).
- Integration test builds its own `CustomUserDetails`-backed `Authentication` for the STAFF case (see `staff()` helper) rather than the plan's literal `.with(user("staff").roles("STAFF"))` snippet, because the controller's `@AuthenticationPrincipal CustomUserDetails principal` parameter requires a `CustomUserDetails` principal, which the default Spring Security Test `user(...)` builder does not produce (it produces a plain `org.springframework.security.core.userdetails.User`). This exact pattern already exists in `TableOperationIntegrationTest.staff()` for the same reason.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Corrected STAFF authentication helper in the integration test**
- **Found during:** Task 2 (writing `KitchenIntegrationTest`)
- **Issue:** The plan's suggested test snippet, `.with(user("staff").roles("STAFF"))`, builds a plain Spring Security Test `User` principal. `KitchenController.advanceItemStatus` reads `@AuthenticationPrincipal CustomUserDetails principal` and calls `principal.getId()` — with the plan's suggested builder this would throw a `ClassCastException` (or resolve to `null`) rather than exercising the real advance path.
- **Fix:** Added a `staff()` helper that builds a `CustomUserDetails` and wraps it in a `UsernamePasswordAuthenticationToken`, applied via `.with(authentication(...))` — matching the existing `TableOperationIntegrationTest.staff()` convention for the same controller-principal-type reason. USER-role and anonymous denial assertions still use the plain `user(...)`/no-auth request since those never reach the controller method (rejected by the security filter chain first).
- **Files modified:** `src/test/java/com/example/feat1/DDD/kitchen_context/integration/KitchenIntegrationTest.java`
- **Verification:** `./mvnw -o test -Dtest=KitchenIntegrationTest` — 3/3 tests pass, including the 200/PREPARING happy-path assertion that depends on `principal.getId()` resolving correctly.
- **Committed in:** `833f71d` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 bug fix, test-only)
**Impact on plan:** No production code impact; test-only correction needed for the plan's chosen `@AuthenticationPrincipal CustomUserDetails` controller signature to actually be exercised. No scope creep.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- D-05 is fully implemented: staff can advance a single kitchen item and view the active board under the existing RBAC; illegal transitions return a stable error; non-staff are denied.
- `SecurityConfig.java` remains untouched (verified via `git status`/`git diff --name-only` throughout execution).
- Full Maven suite: 191 tests, BUILD SUCCESS (`./mvnw -o clean test`).

---
*Phase: 17-kitchen-context*
*Completed: 2026-07-08*

## Self-Check: PASSED

All created files verified present; all task commits (`402ba21`, `833f71d`) and the summary commit
(`ada257f`) verified present in `git log`.
