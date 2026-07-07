# Deferred Items — Phase 15

Out-of-scope discoveries logged during execution (not fixed — see SCOPE BOUNDARY).

## 15-01

- **Payment/Table Kafka producers use the legacy Jackson-2 `JsonSerializer`.**
  - Files: `src/main/java/com/example/feat1/DDD/payment_context/infrastructure/config/PaymentKafkaProducerConfig.java`, `src/main/java/com/example/feat1/DDD/table_context/infrastructure/config/TableKafkaProducerConfig.java`
  - Discovered while fixing the order producer (15-01 Task 3). On the Boot 4 / Jackson 3 classpath there is no `jackson-datatype-jsr310`, so the Jackson-2 `org.springframework.kafka.support.serializer.JsonSerializer` cannot serialize `Instant` fields. Payment and Table events contain `Instant` fields and would fail to publish at runtime for the same reason the order producer did.
  - Not fixed here: pre-existing (Phases 11/12), in other bounded contexts, not caused by this plan's changes. The order context was migrated to the Jackson-3 `JacksonJsonSerializer`; payment/table should follow the same migration in a dedicated fix.
