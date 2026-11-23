# Security policy

KORA Core moves money, so a security flaw is a financial flaw. Thank you for reporting one
responsibly.

## Reporting a vulnerability

**Do not open a public issue, discussion or pull request for a vulnerability.**

Report it privately through GitHub: on the repository, open the **Security** tab, then
**Report a vulnerability**. The report reaches the maintainer only, and the fix is prepared in
a private advisory before anything is disclosed.

Please include:

- what an attacker can do, and against which component — API endpoint, use case, database
  role, mobile client;
- the steps to reproduce it, against a local stack started as described in
  [docs/development/local-setup.md](./docs/development/local-setup.md);
- the commit you tested;
- the impact as you understand it: money moved or created, funds blocked, data exposed,
  authentication bypassed.

Never include a real secret, token, PIN or personal data in a report.

## What to expect

This is a project maintained in the open, without a security team or a service-level
agreement. Reports are handled on a best-effort basis: acknowledged, reproduced, fixed on a
branch, then disclosed in a GitHub security advisory once the fix is merged, crediting the
reporter unless they prefer otherwise.

## Supported versions

KORA Core has not released a version yet. Fixes land on `develop` and reach `main` with the
next release; there is no older line to patch.

## Scope

In scope: the backend in this repository, its database migrations and roles, its
configuration, and the mobile client in `mobile/`.

Out of scope: the simulated payment providers, which are stubs by design; findings that need a
`.env` you created with weak values yourself; and the known limits already recorded in the
[state of view](./docs/kora-core-state-of-view-v3.md) and in
[`CLAUDE.md` §9](./CLAUDE.md#9-known-gaps--do-not-report), such as the absence of an
idempotency store or of rate limiting.

## What already protects the system

The security model is described in the [README](./README.md#-security). Among others: an
append-only ledger enforced by the database, an application role that cannot change the
schema, signed tokens with one key per use, revocable refresh-token families, PINs hashed with
BCrypt, and sensitive values that redact themselves from logs.
