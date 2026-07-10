# Phase 19: VueJS admin management interface - Research

**Generated:** 2026-07-10
**Status:** Complete

## Stack Decision

Use a separate Vite app under `admin-ui/`:

- Vue 3
- TypeScript
- Vite
- vue-router
- @lucide/vue
- Vitest for unit tests

This keeps the Spring Boot backend build independent while allowing local frontend development with Vite's dev server. A proxy can be added later, but the first implementation should use `VITE_API_BASE_URL` for clarity.

## Architecture

Recommended structure:

```text
admin-ui/
  src/
    api/
      client.ts
      auth.ts
      modules.ts
    components/
      AppShell.vue
      DataTable.vue
      EmptyState.vue
      FormDrawer.vue
      StatusBadge.vue
    layouts/
      AdminLayout.vue
    router/
      index.ts
    stores/
      auth.ts
    views/
      LoginView.vue
      OverviewView.vue
      MenuView.vue
      TablesView.vue
      InventoryView.vue
      OrdersView.vue
      KitchenView.vue
      PaymentsView.vue
```

## Integration Notes

- Backend uses JWT Bearer auth and Spring Security role checks.
- Many admin endpoints return DTO records/lists but do not expose all list APIs needed for every deep entity. The frontend should make available operations obvious and mark backend gaps.
- Payment history filters for status/method/date range are not available yet; do not wire fake query params.
- The backend already has Swagger/OpenAPI available at `/v3/api-docs`, but this phase can defer code generation.

## Risks

| Risk | Mitigation |
|------|------------|
| Backend CORS blocks Vite dev server | Add backend CORS follow-up only if manual smoke test confirms it. |
| Missing admin list endpoints | Show gap states and record follow-up instead of mocking data. |
| Token storage tradeoff | Use localStorage for local-dev MVP; document that hardened cookie/session storage is future work. |
| Large UI scope | Ship a workbench skeleton and representative module primitives first, then expand modules. |

## Recommendation

Plan the phase as a vertical frontend MVP:

1. Scaffold Vue app, auth, router, shell, API client.
2. Build representative admin modules using existing endpoints.
3. Add tests/build verification and manual smoke docs.
