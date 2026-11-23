# KORA Core documentation

Where everything is, by what you want to do.

## Contribute

| Document | Read it to |
|---|---|
| [CONTRIBUTING.md](../CONTRIBUTING.md) | make a change and get it merged: workflow, commits, checklist |
| [development/local-setup.md](./development/local-setup.md) | run the stack: environment file, containers, profiles, IDE |
| [development/testing.md](./development/testing.md) | run the four test levels, read the coverage gate and the API contract |
| [development/database.md](./development/database.md) | write a migration, constrain an enum column, understand the database roles |
| [development/troubleshooting.md](./development/troubleshooting.md) | fix what usually goes wrong |

## Understand the design

| Document | Read it to |
|---|---|
| [engineering-standards.md](./engineering-standards.md) | the principles, the definition of done, the testing strategy, the architecture and the conventions |
| [../CLAUDE.md](../CLAUDE.md) | the invariants, the domain vocabulary and the rules a review applies to a diff |
| [adr/](./adr/README.md) | every significant decision, and what it cost |
| [../api-contract/payments.openapi.json](../api-contract/payments.openapi.json) | the published contract of the payment API |
| [design/steps/](./design/steps) | the design notes of each roadmap step |

## Follow the project

| Document | Read it to |
|---|---|
| [../ROADMAP.md](../ROADMAP.md) | the single roadmap: steps, exit gates, dependencies |
| [kora-core-state-of-view-v3.md](./kora-core-state-of-view-v3.md) | what holds today and what does not, with the ranked limits L-1 to L-15 |
| [kora-core-state-of-view-v2.md](./kora-core-state-of-view-v2.md) · [kora-core-state-of-view.md](./kora-core-state-of-view.md) | earlier reviews, kept as written |
| [technical-foundation.md](./technical-foundation.md) | the quality machinery: CI, coverage, static analysis, supply chain — done and to do |

## Operate

| Document | Read it to |
|---|---|
| [../perf/PERF.md](../perf/PERF.md) | run a load-test campaign with k6 and Grafana |
| [../perf/SIMULATION.md](../perf/SIMULATION.md) | understand the simulated traffic |
| [../SECURITY.md](../SECURITY.md) | report a vulnerability |

## Mobile client

| Document | Read it to |
|---|---|
| [../mobile/README.md](../mobile/README.md) | run Kora Mobile, the React Native client |
| [../mobile/docs/](../mobile/docs/README.md) | its specification: requirements, API contract, design system, architecture |
