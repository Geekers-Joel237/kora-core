# Contributing to KORA Core

Thank you for taking the time to contribute. KORA Core moves money, so the bar is high on
purpose: every change is written test first, every rule is enforced by the build, and every
significant decision has its cost written down. This guide is the path from an idea to a
merged pull request.

## Contents

1. [Before you start](#1-before-you-start)
2. [Set up the project](#2-set-up-the-project)
3. [Workflow](#3-workflow)
4. [Making a change](#4-making-a-change)
5. [Testing and gates](#5-testing-and-gates)
6. [Commit messages](#6-commit-messages)
7. [Pull requests](#7-pull-requests)
8. [Architecture decisions](#8-architecture-decisions)
9. [Reporting bugs and proposing features](#9-reporting-bugs-and-proposing-features)

---

## 1. Before you start

Read, in this order:

1. the [README](./README.md) — what the project is and what runs today;
2. [`CLAUDE.md`](./CLAUDE.md) — the invariants, the vocabulary and the **review rules** a diff
   is checked against;
3. [`docs/engineering-standards.md`](./docs/engineering-standards.md) — how the code is
   written: definition of done, testing strategy, architecture, conventions.

For anything bigger than a fix, open an issue first and say what you intend. A change that
touches financial correctness, a module boundary or the transaction strategy needs an
[ADR](./docs/adr/README.md) before the code.

Security issues are never reported in a public issue — see [SECURITY.md](./SECURITY.md).

## 2. Set up the project

You need Java 21+, Docker and Git; Gradle is wrapped.

```bash
git clone https://github.com/Geekers-Joel237/kora-core.git && cd kora-core
cp .env.example .env          # then generate each secret it lists
docker compose up -d          # PostgreSQL + MailDev
./gradlew bootRun             # http://localhost:8081/swagger-ui.html
```

Each secret in `.env.example` is the command that generates it. The details — profiles,
optional containers, the IDE, what each variable feeds — are in
[docs/development/local-setup.md](./docs/development/local-setup.md).

## 3. Workflow

KORA Core follows **Git Flow**.

| Branch | From | Into | For |
|---|---|---|---|
| `main` | — | — | the released state; merges only, every merge tagged |
| `develop` | — | — | integration; the target of every pull request, always green |
| `feature/<scope>-<description>` | `develop` | `develop` | a feature or a refactoring |
| `fix/<description>` | `develop` | `develop` | a bug found before release |
| `release/v<major>.<minor>` | `develop` | `main` and `develop` | stabilisation: fixes, docs and version bumps only |
| `hotfix/<description>` | `main` | `main` and `develop` | a production incident, never a feature in disguise |

Examples: `feature/payment-method-providers`, `fix/transfer-deadlock-lock-ordering`.

**Rules**: never commit to `develop` or `main` directly; never force-push either; keep a
branch short-lived and a pull request small.

**Versions** follow `vMAJOR.MINOR.PATCH`: `MINOR` when a roadmap step completes, `PATCH` for a
fix, `MAJOR` for a breaking architecture change such as the first service extraction.

## 4. Making a change

1. **Start from a failing test** that states the behaviour, at the level that owns it — a
   domain rule in a unit test, a query in an integration test, an HTTP boundary in a contract
   test. Test a behaviour through its use case, not a class in isolation.
2. **Write the code where it belongs**: domain rules in `domain/`, use cases in
   `application/command/<intent>/` or `application/query/<intent>/`, framework code in
   `adapters/`, wiring in `config/`
   ([engineering-standards.md §8](./docs/engineering-standards.md#8-code-conventions)).
3. **Keep the invariants** of [`CLAUDE.md` §6](./CLAUDE.md#6-non-negotiable-invariants): money
   is `BigDecimal`, ledger entries are append-only, no provider call inside a database
   transaction, a rollback happens by throwing.
4. **A schema change is a migration**, named `V<yyyyMMddHHmm>__snake_case.sql`
   ([database.md](./docs/development/database.md)).
5. **Update the documentation** the change makes wrong, in the same pull request.

## 5. Testing and gates

```bash
./gradlew unitTest     # the unit level alone, ~15 s, no Docker
./gradlew test         # all four levels; Docker must be running for Testcontainers
./gradlew build        # every level + the coverage gate — what a pull request must pass
```

`./gradlew build` fails on any of these:

| Gate | Fails when |
|---|---|
| Unit, integration, contract and e2e tests | a behaviour regresses |
| Architecture tests (`shared/unit/architecture/`) | a layer points outward, a module couples, a test sits at the wrong level, a secret gains a fallback, a migration is misnamed |
| API contract | a status, content type or body of the payment API changes without its committed file |
| Coverage floors | the hexagon drops below 100 % for payment or 90 % elsewhere, or an all-suite measure drops below its ratchet |

How each gate works, and how to accept an intended contract change, is in
[docs/development/testing.md](./docs/development/testing.md).

## 6. Commit messages

KORA Core uses [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <what the change does, in the imperative>

<why, when it is not obvious from the subject>
```

| Type | When |
|---|---|
| `feat` | a new feature or use case |
| `fix` | a bug fix |
| `refactor` | a code change with no functional impact |
| `test` | adding or fixing tests only |
| `docs` | documentation only |
| `perf` | a performance improvement |
| `build` | the build, its gates and dependencies |
| `chore` | tooling and housekeeping |
| `adr` | an architecture decision record |

**Scopes** name the concept affected: a module — `payment`, `auth`, `shared` — or a concern —
`ledger`, `provider`, `persistence`, `rest`, `coverage`, `perf`, `infra`, `mobile`.

```
feat(payment): route each payment to the provider of its method
fix(payment): release the hold of an expired authorization, leave UNKNOWN to reconciliation
test(auth): drive registration, code verification and login through the use cases
```

One commit, one reason to change. Each commit compiles and passes the unit level on its own.

## 7. Pull requests

Open the pull request against `develop`; the template lists what is checked. Before asking for
a review:

- [ ] the change started from a failing test, and the test now passes;
- [ ] `./gradlew build` is green on your machine;
- [ ] no committed contract file changed, or the change is intended and explained;
- [ ] no coverage floor was lowered, or the reason is in the description;
- [ ] the documentation the change makes wrong is updated;
- [ ] a significant decision comes with its ADR;
- [ ] no secret, no `.env`, no personal data in the diff.

A reviewer applies the rules of [`CLAUDE.md` §7](./CLAUDE.md#7-review-rules), each decidable by
reading the diff alone.

## 8. Architecture decisions

A decision is significant if it affects financial correctness or auditability, a module,
layer or port boundary, the concurrency or transaction isolation strategy, or a trade-off
between performance and correctness. Such a decision gets an ADR, numbered and linked from
[docs/adr/README.md](./docs/adr/README.md), which also gives the format.

## 9. Reporting bugs and proposing features

Use the issue templates: **Bug report** for something that behaves wrongly, **Feature
request** for a proposal. For a bug, give the request, the expected and the observed result,
and the commit you ran. Never paste a token, a PIN, a secret or real personal data.
