# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kora Core is a fintech-grade wallet backend (neobank / Mobile Money) built with Java 21 and Spring Boot 4.0.3. It simulates a production wallet system: P2P transfers, cash-in/out, merchant payments, multi-provider orchestration, settlement, reconciliation, and risk management. The project evolves progressively from a transactional monolith through modular monolith, hexagonal architecture, event-driven patterns, and eventually microservice extraction (see ROADMAP.md for the full 10-stage plan).

## Build & Run Commands

```bash
# Build the project
./gradlew build

# Run the application (starts PostgreSQL via Docker Compose automatically)
./gradlew bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.geekersjoel237.koracore.SomeTestClass"

# Run a single test method
./gradlew test --tests "com.geekersjoel237.koracore.SomeTestClass.someMethod"

# Clean build
./gradlew clean build
```

Docker Compose (`compose.yaml`) provides PostgreSQL locally. Spring Boot Docker Compose support auto-starts it during `bootRun`.

## Tech Stack

- **Java 21** — use records, sealed types, pattern matching where they improve modeling
- **Spring Boot 4.0.3** — Web (REST), Data JPA, Data JDBC, Actuator, DevTools
- **PostgreSQL** — primary database (via Docker Compose locally)
- **Lombok** — compile-time boilerplate reduction
- **springdoc-openapi** — API documentation (Swagger UI)
- **JUnit 5** — testing framework (with Testcontainers for integration tests)

## Architecture & Design Principles

The architecture evolves with the roadmap, currently starting as a monolith with strict separation:

- **Domain layer**: Pure Java, no Spring annotations. Contains entities, value objects, domain services, and invariants.
- **Application layer**: Use cases/command handlers and port interfaces.
- **Infrastructure layer**: Adapters for DB, messaging, and external providers.
- **Web/API layer**: Controllers only — no business logic.

### Bounded Contexts (DDD)

Four primary domains emerge through the roadmap: **Payments**, **Ledger**, **Risk**, **Reconciliation**. Each has explicit boundaries and ownership.

### Key Domain Concepts

- **Double-entry ledger**: Immutable entries, balance is always derived (never updated directly). Invariant: `sum(debits) == sum(credits)`.
- **Payment lifecycle state machine**: `INITIATED → AUTHORIZED → CAPTURED → SETTLED` (+ `FAILED`/`REVERSED`). Transitions must be validated — no illegal state changes.
- **Idempotency**: All financial operations must be idempotent (deduplicated via idempotency keys).
- **Value objects**: `Money(amount, currency)`, `ProviderReference`, `IdempotencyKey` — use records.

### Naming Conventions

- Use business names in the domain: `Order`, `PaymentPart`, `LedgerEntry`
- Avoid technical suffixes in domain types (no "Entity", "DTO" in domain layer)
- Domain errors are explicit typed exceptions, mapped at boundaries

## Testing Strategy (TDD, 3 Layers)

1. **Unit tests** — Domain model, domain services, use cases with mocked ports. No Spring context, no DB, no network. Must run in seconds.
2. **Integration tests** — Repository adapters, DB constraints, transaction behavior. Use Testcontainers and `@DataJpaTest` sliced context.
3. **E2E tests** — Full app + DB via Testcontainers. Test real business flows over HTTP.

## Definition of Done

Every change must have: tests (unit + integration), domain invariants captured, no business logic in controllers/adapters, observability for money movement operations.

## Key Files

- `ROADMAP.md` — 10-stage progressive evolution plan with volume targets
- `HELP.md` — Engineering standards, methodology, testing strategy, DDD/SOLID rules, and project conventions
