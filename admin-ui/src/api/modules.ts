import { apiFetch } from './client'

function withQuery(
  path: string,
  params: Record<string, string | number | boolean | undefined | null>,
): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') {
      continue
    }
    search.set(key, String(value))
  }
  const query = search.toString()
  return query ? `${path}?${query}` : path
}

/** Known backend gaps surfaced in the UI instead of being silently mocked as complete. */
export type KnownGap = { label: string; detail: string }

export const knownGaps: Record<string, KnownGap[]> = {
  menu: [
    {
      label: 'Admin category/dish listing',
      detail:
        'No GET list endpoint exists for admin categories/dishes. This view reads the public active-menu tree (GET /menus/public) instead, so inactive or archived items will not appear here.',
    },
  ],
  tables: [
    {
      label: 'Reservation listing',
      detail:
        'No GET list endpoint exists for reservations. Only create, status update, and seat are available; reservations created in this session are shown below until the page reloads.',
    },
  ],
  payments: [
    {
      label: 'Status, method, and date-range filters',
      detail:
        'Deferred to backlog 999.1. GET /admin/payments currently only supports orderId, orderUserId, cursor, and size — the filter controls below stay disabled until backend support ships.',
    },
  ],
  orders: [
    {
      label: 'Global order search',
      detail:
        'No admin order-listing endpoint exists yet. Use an order ID surfaced from Kitchen, Payments, or Table sessions to cancel an order or line here.',
    },
  ],
}

// ---------------------------------------------------------------------------
// Menu
// ---------------------------------------------------------------------------

export type MenuStatus = 'ACTIVE' | 'INACTIVE' | 'ARCHIVED'
export type RecipeTargetType = 'DISH' | 'TOPPING_OPTION'

export type PublicToppingOption = { id: string; name: string; additionalPrice: number; sortOrder: number }
export type PublicToppingGroup = {
  id: string
  name: string
  minSelections: number
  maxSelections: number
  sortOrder: number
  options: PublicToppingOption[]
}
export type PublicDish = {
  id: string
  name: string
  description: string
  basePrice: number
  sortOrder: number
  toppingGroups: PublicToppingGroup[]
}
export type PublicCategory = {
  id: string
  name: string
  description: string
  sortOrder: number
  dishes: PublicDish[]
}
export type PublicMenuResponse = { categories: PublicCategory[] }

export type CategoryRequest = { name: string; description?: string; sortOrder?: number; status?: MenuStatus }
export type CategoryResponse = { id: string; name: string; description: string; sortOrder: number; status: MenuStatus }

export type DishRequest = {
  categoryId: string
  name: string
  description?: string
  basePrice: number
  status?: MenuStatus
  sortOrder?: number
}
export type DishResponse = {
  id: string
  categoryId: string
  name: string
  description: string
  basePrice: number
  status: MenuStatus
  sortOrder: number
}

export type ToppingGroupRequest = {
  dishId: string
  name: string
  minSelections: number
  maxSelections: number
  sortOrder?: number
}
export type ToppingGroupResponse = {
  id: string
  dishId: string
  name: string
  minSelections: number
  maxSelections: number
  sortOrder: number
}

export type ToppingOptionRequest = {
  toppingGroupId: string
  name: string
  additionalPrice: number
  status?: MenuStatus
  sortOrder?: number
}
export type ToppingOptionResponse = {
  id: string
  toppingGroupId: string
  name: string
  additionalPrice: number
  status: MenuStatus
  sortOrder: number
}

export type MenuCostingItem = {
  dishId: string
  dishName: string
  sellPrice: number
  estimatedCost: number
  grossMarginAmount: number
  grossMarginPercent: number
  fullyCosted: boolean
  uncostedLineCount: number
}
export type MenuCostingResponse = { items: MenuCostingItem[] }

export const menuApi = {
  getPublicMenu: () => apiFetch<PublicMenuResponse>('/menus/public'),
  createCategory: (body: CategoryRequest) =>
    apiFetch<CategoryResponse>('/admin/menu/categories', { method: 'POST', body: JSON.stringify(body) }),
  archiveCategory: (id: string) =>
    apiFetch<CategoryResponse>(`/admin/menu/categories/${id}`, { method: 'DELETE' }),
  createDish: (body: DishRequest) =>
    apiFetch<DishResponse>('/admin/menu/dishes', { method: 'POST', body: JSON.stringify(body) }),
  archiveDish: (id: string) => apiFetch<DishResponse>(`/admin/menu/dishes/${id}`, { method: 'DELETE' }),
  createToppingGroup: (body: ToppingGroupRequest) =>
    apiFetch<ToppingGroupResponse>('/admin/menu/topping-groups', { method: 'POST', body: JSON.stringify(body) }),
  createToppingOption: (body: ToppingOptionRequest) =>
    apiFetch<ToppingOptionResponse>('/admin/menu/topping-options', { method: 'POST', body: JSON.stringify(body) }),
  archiveToppingOption: (id: string) =>
    apiFetch<ToppingOptionResponse>(`/admin/menu/topping-options/${id}`, { method: 'DELETE' }),
  getRecipe: (targetType: RecipeTargetType, targetId: string) =>
    apiFetch<unknown>(withQuery('/admin/menu/recipes', { targetType, targetId })),
  getMenuCosting: () => apiFetch<MenuCostingResponse>('/admin/menu/costing'),
}

// ---------------------------------------------------------------------------
// Tables
// ---------------------------------------------------------------------------

export type TableStatus = 'ACTIVE' | 'INACTIVE' | 'ARCHIVED'
export type TableOccupancyState = 'AVAILABLE' | 'OCCUPIED' | 'RESERVED' | 'CLEANING' | 'OUT_OF_SERVICE'
export type TableSessionStatus = 'OPEN' | 'CLOSED' | 'CANCELLED'
export type ReservationStatus = 'PENDING' | 'CONFIRMED' | 'SEATED' | 'CANCELLED' | 'NO_SHOW' | 'COMPLETED'

export type DiningAreaRequest = { name: string; sortOrder?: number; status?: TableStatus }
export type DiningAreaResponse = { id: string; name: string; sortOrder: number; status: TableStatus }

export type DiningTableRequest = {
  areaId: string
  code: string
  name: string
  capacity?: number
  sortOrder?: number
  status?: TableStatus
}
export type DiningTableResponse = {
  id: string
  areaId: string
  areaName: string
  code: string
  name: string
  capacity: number | null
  sortOrder: number
  status: TableStatus
}

export type TableSessionResponse = {
  sessionId: string
  tableId: string
  tableCode: string
  tableName: string
  areaId: string
  areaName: string
  status: TableSessionStatus
  partySize: number | null
  note: string | null
  reservationId: string | null
  openedAt: string | null
  closedAt: string | null
  cancelledAt: string | null
}

export type TableReservationResponse = {
  reservationId: string
  tableId: string
  tableCode: string
  tableName: string
  areaId: string
  areaName: string
  customerName: string
  customerPhone: string | null
  customerEmail: string | null
  partySize: number
  startTime: string
  endTime: string
  status: ReservationStatus
  note: string | null
  createdAt: string
  updatedAt: string
}

export type TableOccupancyResponse = {
  tableId: string
  tableCode: string
  tableName: string
  areaId: string
  areaName: string
  capacity: number | null
  state: TableOccupancyState
  activeSession: TableSessionResponse | null
  activeReservation: TableReservationResponse | null
}

export type OpenTableSessionRequest = { partySize?: number; note?: string; reservationId?: string }
export type CloseTableSessionRequest = { nextState: TableOccupancyState; note?: string }
export type CancelTableSessionRequest = { reason?: string }
export type SetTableOccupancyRequest = { state: TableOccupancyState; reason?: string }
export type CreateReservationRequest = {
  tableId: string
  customerName: string
  customerPhone?: string
  customerEmail?: string
  partySize: number
  startTime: string
  endTime: string
  note?: string
}
export type UpdateReservationStatusRequest = { status: ReservationStatus; note?: string }

export const tablesApi = {
  listAreas: () => apiFetch<DiningAreaResponse[]>('/admin/tables/areas'),
  createArea: (body: DiningAreaRequest) =>
    apiFetch<DiningAreaResponse>('/admin/tables/areas', { method: 'POST', body: JSON.stringify(body) }),
  archiveArea: (id: string) => apiFetch<DiningAreaResponse>(`/admin/tables/areas/${id}`, { method: 'DELETE' }),
  listTables: () => apiFetch<DiningTableResponse[]>('/admin/tables'),
  createTable: (body: DiningTableRequest) =>
    apiFetch<DiningTableResponse>('/admin/tables', { method: 'POST', body: JSON.stringify(body) }),
  archiveTable: (id: string) => apiFetch<DiningTableResponse>(`/admin/tables/${id}`, { method: 'DELETE' }),
  listOccupancy: () => apiFetch<TableOccupancyResponse[]>('/admin/tables/occupancy'),
  setOccupancy: (tableId: string, body: SetTableOccupancyRequest) =>
    apiFetch<TableOccupancyResponse>(`/admin/tables/${tableId}/occupancy`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),
  openSession: (tableId: string, body?: OpenTableSessionRequest) =>
    apiFetch<TableSessionResponse>(`/admin/tables/${tableId}/sessions`, {
      method: 'POST',
      body: JSON.stringify(body ?? {}),
    }),
  closeSession: (sessionId: string, body: CloseTableSessionRequest) =>
    apiFetch<TableSessionResponse>(`/admin/table-sessions/${sessionId}/close`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  cancelSession: (sessionId: string, body?: CancelTableSessionRequest) =>
    apiFetch<TableSessionResponse>(`/admin/table-sessions/${sessionId}/cancel`, {
      method: 'POST',
      body: JSON.stringify(body ?? {}),
    }),
  createReservation: (body: CreateReservationRequest) =>
    apiFetch<TableReservationResponse>('/admin/tables/reservations', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateReservationStatus: (reservationId: string, body: UpdateReservationStatusRequest) =>
    apiFetch<TableReservationResponse>(`/admin/tables/reservations/${reservationId}/status`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),
  seatReservation: (reservationId: string) =>
    apiFetch<TableSessionResponse>(`/admin/tables/reservations/${reservationId}/seat`, { method: 'POST' }),
}

// ---------------------------------------------------------------------------
// Inventory
// ---------------------------------------------------------------------------

export type IngredientStatus = 'ACTIVE' | 'ARCHIVED'
export type InventoryMovementType =
  | 'RECEIPT'
  | 'ADJUSTMENT_IN'
  | 'ADJUSTMENT_OUT'
  | 'WASTE'
  | 'STOCK_COUNT'
  | 'CONSUMPTION'
  | 'RESERVATION_RELEASE'

export type IngredientRequest = { name: string; baseUnit: string; description?: string; status?: IngredientStatus }
export type IngredientResponse = {
  ingredientId: string
  name: string
  baseUnit: string
  description: string | null
  status: IngredientStatus
  createdAt: string
  updatedAt: string
}

export type IngredientCostRequest = {
  unitCost: number
  costUnit: string
  effectiveAt?: string
  source?: string
  note?: string
}
export type IngredientCostResponse = {
  costId: string
  ingredientId: string
  unitCost: number
  costUnit: string
  effectiveAt: string
  source: string | null
  note: string | null
  createdAt: string
}

export type StockMovementRequest = {
  ingredientId: string
  type: InventoryMovementType
  quantity: number
  unit: string
  note?: string
  referenceType?: string
  referenceId?: string
  lowStockThreshold?: number
}
export type StockMovementResponse = {
  movementId: string
  ingredientId: string
  ingredientName: string
  type: InventoryMovementType
  quantity: number
  unit: string
  baseQuantityDelta: number
  baseUnit: string
  resultingBalance: number
  note: string | null
  referenceType: string | null
  referenceId: string | null
  actorId: string | null
  createdAt: string
}

export type StockBalanceResponse = {
  ingredientId: string
  ingredientName: string
  ingredientStatus: IngredientStatus
  quantityOnHand: number
  baseUnit: string
  lowStockThreshold: number | null
  lowStock: boolean
  lastMovementAt: string | null
}

export const inventoryApi = {
  listIngredients: (search?: string) =>
    apiFetch<IngredientResponse[]>(withQuery('/admin/inventory/ingredients', { search })),
  createIngredient: (body: IngredientRequest) =>
    apiFetch<IngredientResponse>('/admin/inventory/ingredients', { method: 'POST', body: JSON.stringify(body) }),
  archiveIngredient: (id: string) =>
    apiFetch<IngredientResponse>(`/admin/inventory/ingredients/${id}`, { method: 'DELETE' }),
  addCost: (ingredientId: string, body: IngredientCostRequest) =>
    apiFetch<IngredientCostResponse>(`/admin/inventory/ingredients/${ingredientId}/costs`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  listCosts: (ingredientId: string) =>
    apiFetch<IngredientCostResponse[]>(`/admin/inventory/ingredients/${ingredientId}/costs`),
  listStock: () => apiFetch<StockBalanceResponse[]>('/admin/inventory/stock'),
  listLowStock: () => apiFetch<StockBalanceResponse[]>('/admin/inventory/low-stock'),
  recordMovement: (body: StockMovementRequest) =>
    apiFetch<StockMovementResponse>('/admin/inventory/movements', { method: 'POST', body: JSON.stringify(body) }),
  listMovements: (ingredientId?: string, size = 50) =>
    apiFetch<StockMovementResponse[]>(withQuery('/admin/inventory/movements', { ingredientId, size })),
}

// ---------------------------------------------------------------------------
// Payments
// ---------------------------------------------------------------------------

export type PaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'QR_CODE'

export type RecordPaymentRequest = {
  amount: number
  method: PaymentMethod
  idempotencyKey: string
  reference?: string
  note?: string
}
export type RecordRefundRequest = { amount: number; idempotencyKey: string; reason?: string }

export type RefundResponse = { refundId: string; paymentId: string; amount: number; reason: string | null; createdAt: string }
export type PaymentResponse = {
  paymentId: string
  orderId: string
  orderUserId: string
  amount: number
  method: PaymentMethod
  status: string
  reference: string | null
  note: string | null
  createdAt: string
  refunds: RefundResponse[]
}
export type PaymentHistoryResponse = { items: PaymentResponse[]; nextCursor: string | null; hasMore: boolean }

export const paymentsApi = {
  listPayments: (params: { orderId?: string; orderUserId?: string; cursor?: string; size?: number } = {}) =>
    apiFetch<PaymentHistoryResponse>(withQuery('/admin/payments', params)),
  recordPayment: (orderId: string, body: RecordPaymentRequest) =>
    apiFetch<PaymentResponse>(`/admin/orders/${orderId}/payments`, { method: 'POST', body: JSON.stringify(body) }),
  recordRefund: (paymentId: string, body: RecordRefundRequest) =>
    apiFetch<RefundResponse>(`/admin/payments/${paymentId}/refunds`, { method: 'POST', body: JSON.stringify(body) }),
}

// ---------------------------------------------------------------------------
// Kitchen
// ---------------------------------------------------------------------------

export type KitchenItemStatus = 'QUEUED' | 'PREPARING' | 'READY' | 'SERVED' | 'COMPLETED' | 'CANCELLED'

export type KitchenBoardItem = {
  itemId: string
  orderId: string
  orderLineId: string
  dishName: string
  quantity: number
  status: KitchenItemStatus
}

export const kitchenForwardStatus: Record<KitchenItemStatus, KitchenItemStatus | null> = {
  QUEUED: 'PREPARING',
  PREPARING: 'READY',
  READY: 'SERVED',
  SERVED: 'COMPLETED',
  COMPLETED: null,
  CANCELLED: null,
}

export const kitchenApi = {
  getBoard: () => apiFetch<KitchenBoardItem[]>('/admin/orders/kitchen-board'),
  advanceItem: (orderId: string, itemId: string, targetStatus: KitchenItemStatus) =>
    apiFetch<KitchenBoardItem>(`/admin/orders/${orderId}/items/${itemId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ targetStatus }),
    }),
}

// ---------------------------------------------------------------------------
// Orders (admin cancellation)
// ---------------------------------------------------------------------------

export type OrderStatus =
  | 'SUBMITTED'
  | 'PENDING_CONFIRMATION'
  | 'CONFIRMED'
  | 'PREPARING'
  | 'READY'
  | 'SERVED'
  | 'COMPLETED'
  | 'REJECTED'
  | 'CANCELLED'

export type OrderCancellationResponse = {
  orderId: string
  status: OrderStatus
  total: number
  cancelledLineIds: string[]
}

export const ordersApi = {
  cancelOrder: (orderId: string) =>
    apiFetch<OrderCancellationResponse>(`/admin/orders/${orderId}/cancel`, { method: 'POST' }),
  cancelOrderLine: (orderId: string, lineId: string) =>
    apiFetch<OrderCancellationResponse>(`/admin/orders/${orderId}/items/${lineId}/cancel`, { method: 'POST' }),
}
