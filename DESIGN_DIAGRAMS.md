<!-- generated-by: gsd-doc-writer -->
# Design Diagrams

Tài liệu này gom các sơ đồ Mermaid cho hệ thống `feat1`. Các sơ đồ bám theo code hiện tại trong backend Spring Boot, admin UI Vue, Kafka/outbox và các bounded context dưới `src/main/java/com/example/feat1/DDD`.

## 1. Runtime Deployment

```mermaid
flowchart LR
  Browser["Browser"]
  AdminUI["Admin UI\nVue + Vite"]
  Backend["Backend\nSpring Boot"]
  MySQL["MySQL 8.4"]
  Redis["Redis 7.2"]
  Kafka["Kafka 3.9\nKRaft single node"]
  Swagger["Swagger UI\n/swagger-ui.html"]

  Browser --> AdminUI
  AdminUI -->|"HTTP JSON\nVITE_API_BASE_URL"| Backend
  Browser -->|"HTTP JSON"| Backend
  Browser --> Swagger
  Swagger --> Backend
  Backend -->|"JPA"| MySQL
  Backend -->|"Refresh token cache"| Redis
  Backend -->|"Kafka producers/consumers"| Kafka
```

## 2. Bounded Context Map

```mermaid
flowchart TB
  Auth["Auth Context"]
  Identity["Identity Context"]
  Menu["Menu Context"]
  Table["Table Context"]
  Order["Order Context"]
  Inventory["Inventory Context"]
  Kitchen["Kitchen Context"]
  Payment["Payment Context"]
  Outbox["Shared Outbox"]
  Kafka["Kafka"]
  MySQL["MySQL"]

  Auth -->|"uses user/credential/role"| Identity
  Order -->|"MenuQuotePort"| Menu
  Order -->|"TableValidationPort"| Table
  Order -->|"PaymentSummaryPort"| Payment
  Payment -->|"OrderPaymentLookupPort"| Order
  Inventory -->|"MenuRecipeCostingPort"| Menu
  Inventory -->|"OrderLineLookupPort"| Order

  Order --> Outbox
  Inventory --> Outbox
  Outbox --> Kafka
  Kafka --> Inventory
  Kafka --> Order
  Kafka --> Kitchen
  Kafka --> Payment

  Auth --> MySQL
  Identity --> MySQL
  Menu --> MySQL
  Table --> MySQL
  Order --> MySQL
  Inventory --> MySQL
  Kitchen --> MySQL
  Payment --> MySQL
  Outbox --> MySQL
```

## 3. HTTP Layer To Application Services

```mermaid
flowchart TB
  AuthController["AuthController\n/auth/**"]
  UserController["UserController\n/users/**"]
  PublicMenuController["PublicMenuController\n/menus/public"]
  AdminMenuController["AdminMenuController\n/admin/menu/**"]
  TableControllers["Table Controllers\n/tables/**, /admin/tables/**"]
  CartController["CartController\n/cart/**"]
  OrderController["Order Controllers\n/orders/**, /admin/orders/**"]
  InventoryControllers["Inventory Controllers\n/admin/inventory/**"]
  KitchenController["KitchenController\n/admin/orders/kitchen-board"]
  PaymentController["PaymentController\npayments APIs"]

  AuthServices["AuthService\nTokenSerivce\nAuthSessionService"]
  UserService["UserService"]
  MenuService["MenuCatalogService\nMenuOrderValidationService"]
  TableService["TableCatalogService\nTableOperationService"]
  CartService["CartService"]
  OrderServices["OrderSubmissionService\nOrderCancellationService"]
  InventoryServices["InventoryStockService\nInventoryCostingService"]
  KitchenServices["KitchenBoardService\nKitchenTicketAdvanceService"]
  PaymentService["PaymentService"]

  AuthController --> AuthServices
  UserController --> UserService
  PublicMenuController --> MenuService
  AdminMenuController --> MenuService
  TableControllers --> TableService
  CartController --> CartService
  OrderController --> OrderServices
  InventoryControllers --> InventoryServices
  KitchenController --> KitchenServices
  PaymentController --> PaymentService
```

## 4. Authentication And Refresh Flow

```mermaid
sequenceDiagram
  participant UI as Admin UI / Client
  participant AuthController
  participant AuthService
  participant Provider as LocalAuthProvider / GoogleAuthProvider
  participant TokenService as TokenSerivce
  participant RefreshRepo as IRefreshTokenRepository
  participant Redis as RefreshTokenCache
  participant API as Protected API
  participant Filter as JwtAuthenticationFilter

  UI->>AuthController: POST /auth/login or /auth/google
  AuthController->>AuthService: login(request)
  AuthService->>Provider: authenticate(request)
  Provider->>TokenService: generateAccessToken(user, metadata)
  TokenService->>RefreshRepo: save RefreshTokenEntity
  TokenService->>Redis: put refresh token
  TokenService-->>UI: AuthResponse(accessToken, refreshToken)

  UI->>API: Authorization: Bearer accessToken
  API->>Filter: filter request
  Filter->>Filter: validate JWT + tokenType ACCESS
  Filter-->>API: SecurityContext authenticated

  UI->>AuthController: POST /auth/refresh
  AuthController->>TokenService: refresh(refreshToken)
  TokenService->>RefreshRepo: find token
  TokenService->>Redis: evict old, put new
  TokenService-->>UI: rotated token pair
```

## 5. Order Confirmation Saga

```mermaid
sequenceDiagram
  participant User
  participant CartService
  participant OrderSubmissionService
  participant OutboxWriter
  participant OutboxRelay
  participant Kafka
  participant InventoryReservationService
  participant OrderConfirmationService
  participant KitchenTicketCreationService

  User->>CartService: add/update cart item
  User->>OrderSubmissionService: POST /orders
  OrderSubmissionService->>OrderSubmissionService: persist OrderEntity(PENDING_CONFIRMATION)
  OrderSubmissionService->>OutboxWriter: save OrderCreatedEvent
  OutboxRelay->>Kafka: publish orders.created
  Kafka->>InventoryReservationService: OrderCreatedEvent
  InventoryReservationService->>InventoryReservationService: compute recipe requirements
  InventoryReservationService->>InventoryReservationService: lock stock balances
  alt đủ tồn kho
    InventoryReservationService->>InventoryReservationService: reserve stock
    InventoryReservationService->>OutboxWriter: save OrderStockResultEvent(CONFIRMED)
  else thiếu tồn kho
    InventoryReservationService->>OutboxWriter: save OrderStockResultEvent(REJECTED)
  end
  OutboxRelay->>Kafka: publish inventory.order-stock-results
  Kafka->>OrderConfirmationService: OrderStockResultEvent
  alt CONFIRMED
    OrderConfirmationService->>OrderConfirmationService: set order CONFIRMED
    OrderConfirmationService->>OutboxWriter: save OrderConfirmedEvent
    OutboxRelay->>Kafka: publish orders.confirmed
    Kafka->>KitchenTicketCreationService: OrderConfirmedEvent
    KitchenTicketCreationService->>KitchenTicketCreationService: create KitchenTicketEntity
  else REJECTED
    OrderConfirmationService->>OrderConfirmationService: set order REJECTED + rejectionReason
  end
```

## 6. Cancellation Fan-Out

```mermaid
flowchart LR
  CancelAPI["OrderController / AdminOrderCancellationController"]
  CancelService["OrderCancellationService"]
  Outbox["OutboxWriter + OutboxRelay"]
  Kafka["Kafka topic\norders.cancelled"]
  Kitchen["KitchenTicketInvalidationService"]
  Inventory["InventoryReservationReleaseService"]
  Payment["PaymentAutoRefundService"]

  CancelAPI --> CancelService
  CancelService -->|"save OrderCancelledEvent"| Outbox
  Outbox --> Kafka
  Kafka --> Kitchen
  Kafka --> Inventory
  Kafka --> Payment
```

## 7. Kitchen Status And Inventory Settlement

```mermaid
sequenceDiagram
  participant Staff
  participant KitchenController
  participant Advance as KitchenTicketAdvanceService
  participant StatusPublisher as KafkaKitchenTicketStatusChangedPublisher
  participant SettlePublisher as KafkaKitchenSettleTriggerPublisher
  participant Kafka
  participant OrderProjection as KitchenStatusProjectionService
  participant InventorySettlement as InventoryReservationSettlementService

  Staff->>KitchenController: PATCH /admin/orders/{orderId}/items/{itemId}/status
  KitchenController->>Advance: advance(orderId, itemId, targetStatus)
  Advance->>Advance: validate forward-only transition
  Advance->>Advance: update KitchenTicketItemEntity
  Advance->>StatusPublisher: afterCommit publish KitchenTicketStatusChangedEvent
  Advance->>SettlePublisher: afterCommit publish SettleTriggerEvent when needed
  StatusPublisher->>Kafka: kitchen.ticket-status-changed
  SettlePublisher->>Kafka: kitchen.settlement-trigger
  Kafka->>OrderProjection: update order kitchen status projection
  Kafka->>InventorySettlement: settle reserved stock to consumption
```

## 8. Transactional Outbox Lifecycle

```mermaid
stateDiagram-v2
  [*] --> PENDING: OutboxWriter.save\ninside business transaction
  PENDING --> SENT: OutboxRowPublisher\nKafka ack received
  PENDING --> PENDING: publish failed\nattempts < 10
  PENDING --> FAILED: publish failed\nattempts >= 10
  SENT --> [*]
  FAILED --> [*]
```

```mermaid
sequenceDiagram
  participant Service as @Transactional Service
  participant DB as MySQL
  participant OutboxWriter
  participant Relay as OutboxRelay
  participant Publisher as OutboxRowPublisher
  participant Kafka

  Service->>DB: save business entity
  Service->>OutboxWriter: save event row
  OutboxWriter->>DB: insert OutboxEventEntity(PENDING)
  Service->>DB: commit business + outbox
  Relay->>DB: claim pending rows
  Relay->>Publisher: publish(row)
  Publisher->>Kafka: send topic/key/payload
  Kafka-->>Publisher: ack
  Publisher->>DB: mark SENT
```

## 9. Payment Flow

```mermaid
sequenceDiagram
  participant Staff
  participant PaymentController
  participant PaymentService
  participant OrderLookup as OrderPaymentLookupPort
  participant PaymentRepo as PaymentRepository
  participant Publisher as KafkaPaymentEventPublisher
  participant Kafka

  Staff->>PaymentController: POST /admin/orders/{orderId}/payments
  PaymentController->>PaymentService: recordPayment(actorUserId, orderId, request)
  PaymentService->>PaymentService: validate idempotencyKey + amount
  PaymentService->>PaymentRepo: findByOrderIdAndIdempotencyKey
  alt duplicate idempotency key
    PaymentService-->>PaymentController: existing PaymentResponse
  else new payment
    PaymentService->>OrderLookup: getSubmittedOrder(orderId)
    PaymentService->>PaymentService: summarize totals + prevent overpay
    PaymentService->>PaymentRepo: save PaymentEntity
    PaymentService->>Publisher: afterCommit publish PAYMENT_RECORDED
    Publisher->>Kafka: payments.events
    PaymentService-->>PaymentController: PaymentResponse
  end
```

## 10. High-Level Data Relationships

```mermaid
erDiagram
  USER ||--o{ REFRESH_TOKEN : owns
  USER ||--o{ ORDER_CART : owns
  USER ||--o{ ORDER : submits
  ORDER_CART ||--o{ ORDER_CART_LINE : contains
  ORDER ||--o{ ORDER_LINE : contains
  ORDER ||--o{ PAYMENT : paid_by
  PAYMENT ||--o{ PAYMENT_REFUND : refunded_by
  ORDER ||--o| KITCHEN_TICKET : creates
  KITCHEN_TICKET ||--o{ KITCHEN_TICKET_ITEM : contains
  DINING_AREA ||--o{ DINING_TABLE : contains
  DINING_TABLE ||--o{ TABLE_SESSION : opens
  DINING_TABLE ||--o{ TABLE_RESERVATION : reserves
  MENU_CATEGORY ||--o{ DISH : contains
  DISH ||--o{ TOPPING_GROUP : has
  TOPPING_GROUP ||--o{ TOPPING_OPTION : contains
  DISH ||--o{ RECIPE : costed_by
  INGREDIENT ||--o{ INGREDIENT_COST : priced_by
  INGREDIENT ||--o{ INVENTORY_STOCK_BALANCE : tracked_by
  INGREDIENT ||--o{ INVENTORY_STOCK_MOVEMENT : moved_by
  ORDER ||--o{ STOCK_RESERVATION : reserves
  OUTBOX_EVENT }o--|| KAFKA_TOPIC : publishes_to
```

## 11. Admin UI Module Map

```mermaid
flowchart TB
  Router["vue-router\nadmin-ui/src/router/index.ts"]
  AuthStore["auth store\nstores/auth.ts"]
  ApiClient["apiFetch\napi/client.ts"]
  Modules["API modules\napi/modules.ts"]
  Views["Views"]
  Components["Shared components"]

  Views --> Components
  Views --> Modules
  Modules --> ApiClient
  ApiClient --> AuthStore
  Router --> AuthStore

  Views --> Overview["OverviewView"]
  Views --> Menu["MenuView"]
  Views --> Tables["TablesView"]
  Views --> Inventory["InventoryView"]
  Views --> Payments["PaymentsView"]
  Views --> Kitchen["KitchenView"]
  Views --> Orders["OrdersView"]
  Views --> Login["LoginView"]
```

## 12. Layered Dependency Rule

```mermaid
flowchart BT
  Entity["JPA Entity\ninfrastructure/entity"]
  Repository["Spring Data Repository\ninfrastructure/repository"]
  Adapter["Adapters\ninfrastructure/adapter"]
  Controller["REST Controller\ninfrastructure/presentation"]
  Service["Application Service\napplication"]
  DTO["DTO\napplication/dto"]
  Event["Event\napplication/event"]
  Domain["Domain Model + Port\ndomain"]

  Controller --> DTO
  Controller --> Service
  Service --> Domain
  Service --> Repository
  Service --> Event
  Repository --> Entity
  Adapter --> Service
  Adapter --> Domain
```
