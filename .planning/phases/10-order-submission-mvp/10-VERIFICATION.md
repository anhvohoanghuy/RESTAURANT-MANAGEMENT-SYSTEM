# Phase 10 Verification

## Command

```powershell
$env:JAVA_HOME='C:\Users\chinh\.jdks\ms-17.0.19'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd test
```

## Result

- Status: Passed
- Tests run: 92
- Failures: 0
- Errors: 0
- Skipped: 0

## Coverage Notes

- `OrderSubmissionServiceTest` verifies order snapshot creation, stable submit errors, cart clearing, owner scoping, and `OrderCreated` event payloads.
- `OrderSubmissionIntegrationTest` verifies the authenticated HTTP flow, persisted table/line snapshots, cart clearing, order owner scoping, and mocked event publishing without a real Kafka broker.
