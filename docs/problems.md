# Problem catalog

Every error KORA Core returns is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem
detail, served as `application/problem+json`:

```json
{
  "type": "https://github.com/Geekers-Joel237/kora-core/blob/develop/docs/problems.md#insufficient-funds",
  "title": "Unprocessable Content",
  "status": 422,
  "detail": "Insufficient funds: available 2000 XAF, required 5000 XAF",
  "instance": "/payments/cash-out",
  "code": "INSUFFICIENT_FUNDS",
  "correlationId": "33333333-3333-3333-3333-333333333333"
}
```

- **`code`** is the stable, machine-readable name of the problem. A client switches on it, never
  on `detail`, which is written for people and may change.
- **`type`** points to the problem's entry below.
- **`correlationId`** is present when the request carried one or, for a command, was given one:
  the same value is returned in the `X-Correlation-Id` header. Quote it when you report a
  problem.
- **`violations`** lists each invalid field on a `VALIDATION_FAILED`.

A new code is added here in the same pull request as the exception that raises it; a test in
each module fails when a code is missing from this page.

| Code | Status | Raised when |
|---|---|---|
| [`VALIDATION_FAILED`](#validation-failed) | 400 | a request field breaks its constraint |
| [`MALFORMED_REQUEST`](#malformed-request) | 400 | the body is not readable JSON |
| [`INVALID_ARGUMENT`](#invalid-argument) | 400 | a value is rejected once read: an unknown payment method, a missing reason |
| [`UNAUTHENTICATED`](#unauthenticated) | 401 | the access token is missing, invalid or expired |
| [`TOKEN_INVALID`](#token-invalid) | 401 | a refresh or logout token is refused |
| [`INVALID_PIN`](#invalid-pin) | 401 | the PIN does not match |
| [`INVALID_OTP`](#invalid-otp) | 401 | the one-time code does not match |
| [`OTP_EXPIRED`](#otp-expired) | 401 | the one-time code expired or was already used |
| [`FORBIDDEN`](#forbidden) | 403 | the token's role does not grant the route |
| [`USER_NOT_ACTIVE`](#user-not-active) | 403 | the user is not active |
| [`ACCOUNT_NOT_FOUND`](#account-not-found) | 404 | the wallet, the recipient or the transaction does not exist |
| [`CUSTOMER_NOT_FOUND`](#customer-not-found) | 404 | no customer has this email |
| [`COMMAND_REPLAYED`](#command-replayed) | 409 | a command with this correlation id was already received |
| [`EMAIL_ALREADY_REGISTERED`](#email-already-registered) | 409 | the email belongs to another customer |
| [`INSUFFICIENT_FUNDS`](#insufficient-funds) | 422 | the available balance does not cover the debit |
| [`ACCOUNT_BLOCKED`](#account-blocked) | 422 | the account is blocked |
| [`ACCOUNT_SUSPENDED`](#account-suspended) | 422 | the customer is suspended |
| [`SELF_TRANSFER`](#self-transfer) | 422 | a transfer names the payer as recipient |
| [`INVALID_ACCOUNT`](#invalid-account) | 422 | an account cannot take part in the operation |
| [`INVALID_STATE_TRANSITION`](#invalid-state-transition) | 422 | the transaction's state does not allow the operation |
| [`HOLD_NOT_ACTIVE`](#hold-not-active) | 422 | a funds hold was already resolved |
| [`CURRENCY_MISMATCH`](#currency-mismatch) | 422 | two amounts of different currencies are combined |
| [`PROVIDER_REFUSED`](#provider-refused) | 502 | the payment provider refused the operation |
| [`PROVIDER_TIMEOUT`](#provider-timeout) | 502 | the payment provider did not answer in time |
| [`RETRY_EXHAUSTED`](#retry-exhausted) | 503 | concurrent updates kept clashing after every retry |
| [`MAIL_DELIVERY_FAILED`](#mail-delivery-failed) | 503 | the one-time code could not be mailed |

## Request

### validation-failed

`400` — `VALIDATION_FAILED`. A field breaks its constraint; `violations` names each one with its
message. Fix the request; retrying it unchanged fails again.

### malformed-request

`400` — `MALFORMED_REQUEST`. The body is not readable JSON, or a value has the wrong type.

### invalid-argument

`400` — `INVALID_ARGUMENT`. A value passed validation but is refused once read: an unknown payment
method, a reversal without a reason. `detail` says which.

## Authentication

### unauthenticated

`401` — `UNAUTHENTICATED`. The access token is missing, malformed, expired, or is a refresh token.
Refresh the session once, then replay the request; this is the only problem that calls for a
refresh.

### token-invalid

`401` — `TOKEN_INVALID`. The refresh token is unknown, revoked, expired or already exchanged — a
replay ends its whole session. Sign in again.

### invalid-pin

`401` — `INVALID_PIN`. The PIN does not match. Nothing moved; do not refresh the session.

### invalid-otp

`401` — `INVALID_OTP`. The one-time code does not match. Ask for the code again.

### otp-expired

`401` — `OTP_EXPIRED`. The one-time code expired or was already used. Request a new one.

### forbidden

`403` — `FORBIDDEN`. The token is valid, but its role does not grant this route: an operator on a
customer route, or the reverse.

### user-not-active

`403` — `USER_NOT_ACTIVE`. The user is suspended or not verified, so the session cannot be renewed.

## Not found and conflicts

### account-not-found

`404` — `ACCOUNT_NOT_FOUND`. The caller's wallet, the transfer's recipient, or the transaction to
reverse does not exist.

### customer-not-found

`404` — `CUSTOMER_NOT_FOUND`. No customer has this email.

### command-replayed

`409` — `COMMAND_REPLAYED`. A command with this correlation id was received within the replay
window. Send a new correlation id for a new operation. Not an idempotency guarantee: the original
result is not returned (roadmap Step 4).

### email-already-registered

`409` — `EMAIL_ALREADY_REGISTERED`. The email belongs to another customer.

## Business rules

### insufficient-funds

`422` — `INSUFFICIENT_FUNDS`. The available balance — the ledger balance minus the funds held for
debits in flight — does not cover the amount. Nothing moved.

### account-blocked

`422` — `ACCOUNT_BLOCKED`. The account is blocked and can neither pay nor receive.

### account-suspended

`422` — `ACCOUNT_SUSPENDED`. The customer is suspended.

### self-transfer

`422` — `SELF_TRANSFER`. A transfer names the payer as its own recipient.

### invalid-account

`422` — `INVALID_ACCOUNT`. An account cannot take part in this operation.

### invalid-state-transition

`422` — `INVALID_STATE_TRANSITION`. The transaction's state does not allow the operation — for
example, reversing a payment already reversed, failed or `UNKNOWN`.

### hold-not-active

`422` — `HOLD_NOT_ACTIVE`. The funds hold was already consumed or released.

### currency-mismatch

`422` — `CURRENCY_MISMATCH`. Two amounts of different currencies were combined.

## Upstream and availability

### provider-refused

`502` — `PROVIDER_REFUSED`. The payment provider refused the operation. Nothing moved; the
transaction records the failure.

### provider-timeout

`502` — `PROVIDER_TIMEOUT`. The payment provider did not answer in time. The outcome is not known:
a payment usually ends `UNKNOWN` and is resolved by reconciliation, never by a blind retry.

### retry-exhausted

`503` — `RETRY_EXHAUSTED`. Concurrent updates kept clashing after every retry. On a payment, the
provider may already have answered: the outcome is uncertain.

### mail-delivery-failed

`503` — `MAIL_DELIVERY_FAILED`. The one-time code could not be mailed after retrying. Nothing was
kept; try again later.
