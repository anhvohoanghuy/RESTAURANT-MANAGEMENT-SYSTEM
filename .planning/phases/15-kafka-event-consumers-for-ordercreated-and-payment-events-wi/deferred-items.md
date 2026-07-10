# Deferred Items — Phase 15

Out-of-scope discoveries logged during execution (not fixed — see SCOPE BOUNDARY).

## 15-01 — ✅ RESOLVED (2026-07-10, quick task 260710-eqh)

Payment + Table producer configs migrated to Jackson-3 `JacksonJsonSerializer` (commit `ab09ab1`); full Maven suite 213/213 green. Original note below.

- **Payment/Table Kafka producers use the legacy Jackson-2 `JsonSerializer`.**
  - Files: `src/main/java/com/example/feat1/DDD/payment_context/infrastructure/config/PaymentKafkaProducerConfig.java`, `src/main/java/com/example/feat1/DDD/table_context/infrastructure/config/TableKafkaProducerConfig.java`
  - Discovered while fixing the order producer (15-01 Task 3). On the Boot 4 / Jackson 3 classpath there is no `jackson-datatype-jsr310`, so the Jackson-2 `org.springframework.kafka.support.serializer.JsonSerializer` cannot serialize `Instant` fields. Payment and Table events contain `Instant` fields and would fail to publish at runtime for the same reason the order producer did.
  - Not fixed here: pre-existing (Phases 11/12), in other bounded contexts, not caused by this plan's changes. The order context was migrated to the Jackson-3 `JacksonJsonSerializer`; payment/table should follow the same migration in a dedicated fix.

## 15-06

- **Full `@SpringBootTest` context cannot load in the 15-06 worktree — missing `InventoryStockResultPublisher` bean.**
  - Files: `src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationService.java` (constructor param 5), `src/main/java/com/example/feat1/DDD/inventory_context/domain/port/InventoryStockResultPublisher.java` (port, no implementation in base).
  - Discovered running `./mvnw test` for 15-06 Task 3. `InventoryReservationService` (merged from 15-03) requires an `InventoryStockResultPublisher` bean. Its Kafka adapter implementation is produced by plan **15-04**, a sibling wave-3 plan executed in a separate parallel worktree that is NOT yet merged into the 15-06 base (`57cc058`). Every full-context integration test across all bounded contexts (auth, table, payment, inventory, order — 34 errors) fails to load the ApplicationContext for this one reason; there are 0 test failures (only context-load errors) and 0 errors implicating any 15-06 order-consumer bean.
  - Not fixed here: entirely out of scope for 15-06 (order context). The defect exists at the worktree base independent of this plan's changes and affects contexts 15-06 never touches. Creating a stub `InventoryStockResultPublisher` here would duplicate/collide with 15-04's real adapter on merge. Resolution is automatic: when the orchestrator merges the wave-3 worktrees (15-04 + 15-06) together, 15-04's adapter satisfies the port and the full suite loads. Broker-free unit tests (including the new `OrderKafkaConsumerConfigTest`, 3/3 green, and the phase serde round-trip tests) pass in isolation.
