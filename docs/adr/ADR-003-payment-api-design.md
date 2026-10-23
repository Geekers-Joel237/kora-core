# ADR-003 — Single-Call Payment API Design

**Date**: 2026-04-13
**Status**: Accepted
**Authors**: Kora Core Engineering — Ivan Joël Tchatchoua Bayon
**Related**: ADR-001 — Immutable Double-Entry Ledger · ADR-002 — Payment Lifecycle State Machine

---

## Context

ADR-002 introduced the full payment lifecycle state machine:
`INITIALIZED → AUTHORIZED → CAPTURED → SETTLEMENT_PENDING → SETTLED → COMPLETED`

The question then arises: should the mobile client drive each transition explicitly
(one HTTP call per state), or should the backend orchestrate the full saga
and expose a single client-facing endpoint?

Two options were evaluated:

### Option A — Explicit state transitions per endpoint

```
POST /payments/authorize    → AUTHORIZED
POST /payments/{id}/capture → CAPTURED / SETTLEMENT_PENDING
POST /payments/{id}/reverse → REVERSED
```

The client sequences the calls, handles intermediate states, and retries individually.

### Option B — Single saga endpoint

```
POST /payments → SETTLEMENT_PENDING (or error)
```

The backend orchestrates the full saga atomically. The client sends one call
and receives either success (SETTLEMENT_PENDING) or a typed error.
Intermediate states are internal implementation details.

---

## Decision

**We implement Option B — a single `POST /payments` endpoint that orchestrates
the full saga internally.**

The step-by-step endpoints (`/payments/authorize`, `/payments/{id}/capture`,
`/payments/{id}/reverse`) remain in the codebase as internal capability surfaces,
available for operator tooling, admin dashboards, and future saga orchestration
patterns. They are not removed.

---

## Rationale

### Mobile client simplicity

A mobile Money client making a payment should not have to implement a
state machine in client code. The correct mental model for the client is
"pay → success or failure". Exposing intermediate states as required HTTP
calls pushes retry logic, state storage, and failure recovery into the client.

At Step 1 scale (15,000 tx/day), provider calls (authorize, capture) complete
in well under 500ms each. There is no advantage to splitting them across client
round-trips — the latency saving is negligible, and the added client complexity
is real.

### Atomicity and failure recovery

When authorize succeeds but capture fails, the backend must compensate
(cancel the authorization). Placing the capture call on the client means
the client is responsible for triggering this compensation — creating a class
of bugs where users are left with locked balances because a client-side
error interrupted the flow.

The `executePaymentSaga` method in `PaymentTransactionalExecutor` handles
this compensation internally. A `CAPTURE_FAILED` outcome is never visible
to the mobile client as a state the client must resolve — it is a server-side
error response with an appropriate HTTP status.

### RBAC surface separation

`POST /payments` is restricted to `ROLE_CUSTOMER`. The step-by-step endpoints
and future `/admin/**` surfaces will be restricted to `ROLE_ADMIN` and
`ROLE_OPERATOR` when operator tooling is built (Step 5+). This separation
is enforced at the SecurityConfig level, not at the business logic level,
keeping authorization orthogonal to domain logic.

### Why not keep only the saga endpoint

The individual step endpoints are retained because:
- They are required by the `authorizePayment`, `capturePayment`, `reversePayment`
  use cases, which are tested independently by the Scope 5 E2E tests.
- They will become the backbone of the Step 5 admin/operator dashboard
  (`POST /admin/payments/{id}/capture`, `POST /admin/payments/{id}/reverse`).
- They provide a clean seam for testing individual lifecycle transitions
  without running the full saga.

---

## Consequences

### Positive

- **Client simplicity**: one call, one outcome. No client-side state machine.
- **Server-side compensation**: authorization leaks on capture failure
  are handled atomically by the backend, not delegated to the client.
- **Clean RBAC boundary**: customer surface vs. operator surface are
  separated at the router level.

### Negative / Trade-offs

- **No mid-saga client control**: a client cannot authorize now and capture
  later via `POST /payments`. That flow requires using the explicit endpoints
  (`/payments/authorize`, `/payments/{id}/capture`) directly.
- **Less granular retry control**: if the saga fails at capture, the client
  receives a 422 or 503 and must retry the full saga — it cannot resume
  from the authorized state. At Step 1 latency targets this is acceptable.
  Step 5 will introduce async saga with resume capability if needed.

---

## What Changes at Step 5

When the admin/operator dashboard is built:

- `POST /admin/payments/{id}/capture` — restricted to ROLE_OPERATOR
- `POST /admin/payments/{id}/reverse` — restricted to ROLE_OPERATOR or ROLE_ADMIN
- `GET  /admin/payments?state=AUTHORIZED` — exposes stuck transactions for
  manual intervention

The existing step-by-step endpoints become the implementation backing the
admin surface. `POST /payments` remains the sole client-facing entry point.

---

*Living document. Reviewed at each architecture milestone.*
*github.com/Geekers-Joel237 · linkedin.com/in/geekers-joel237 · geekersjoel237.substack.com*