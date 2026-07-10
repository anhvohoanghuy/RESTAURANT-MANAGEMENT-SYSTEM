import { apiFetch } from './client'

export type ModuleEndpoint = {
  label: string
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  path: string
  status?: 'available' | 'gap'
  note?: string
  sample?: boolean
}

export const moduleEndpoints: Record<string, ModuleEndpoint[]> = {
  menu: [
    { label: 'Create category', method: 'POST', path: '/admin/menu/categories' },
    { label: 'Create dish', method: 'POST', path: '/admin/menu/dishes' },
    { label: 'Create topping group', method: 'POST', path: '/admin/menu/topping-groups' },
    { label: 'Create topping option', method: 'POST', path: '/admin/menu/topping-options' },
    { label: 'Recipe detail', method: 'GET', path: '/admin/menu/recipes' },
    { label: 'Menu costing', method: 'GET', path: '/admin/menu/costing', sample: true },
  ],
  tables: [
    { label: 'Dining areas', method: 'GET', path: '/admin/tables/areas' },
    { label: 'Dining tables', method: 'GET', path: '/admin/tables' },
    { label: 'Occupancy', method: 'GET', path: '/admin/tables/occupancy' },
    { label: 'Availability', method: 'GET', path: '/admin/tables/availability' },
    { label: 'Create reservation', method: 'POST', path: '/admin/tables/reservations' },
  ],
  inventory: [
    { label: 'Ingredients', method: 'GET', path: '/admin/inventory/ingredients' },
    { label: 'Stock balances', method: 'GET', path: '/admin/inventory/stock' },
    { label: 'Low stock', method: 'GET', path: '/admin/inventory/low-stock' },
    { label: 'Stock movements', method: 'GET', path: '/admin/inventory/movements' },
    { label: 'Record movement', method: 'POST', path: '/admin/inventory/movements' },
  ],
  payments: [
    { label: 'Payment history', method: 'GET', path: '/admin/payments' },
    { label: 'Record payment', method: 'POST', path: '/admin/orders/{orderId}/payments' },
    { label: 'Record refund', method: 'POST', path: '/admin/payments/{paymentId}/refunds' },
    {
      label: 'Status/method/date filters',
      method: 'GET',
      path: '/admin/payments',
      status: 'gap',
      note: 'Deferred to Phase 999.1 backend support.',
    },
  ],
  kitchen: [
    { label: 'Kitchen board', method: 'GET', path: '/admin/orders/kitchen-board' },
    { label: 'Advance item status', method: 'PATCH', path: '/admin/orders/{orderId}/items/{itemId}/status' },
  ],
  orders: [
    { label: 'Admin cancel order', method: 'POST', path: '/admin/orders/{orderId}/cancel' },
    { label: 'Admin cancel line', method: 'POST', path: '/admin/orders/{orderId}/items/{lineId}/cancel' },
    {
      label: 'Global order search',
      method: 'GET',
      path: '/admin/orders',
      status: 'gap',
      note: 'No global admin order listing endpoint exists yet.',
    },
  ],
}

export function getModuleSample(moduleKey: string) {
  const endpoint = moduleEndpoints[moduleKey]?.find((item) => item.sample) ?? moduleEndpoints[moduleKey]?.find(
    (item) => item.method === 'GET' && item.status !== 'gap' && !item.path.includes('{'),
  )
  if (!endpoint) {
    return Promise.resolve([])
  }
  return apiFetch<unknown>(endpoint.path)
}
