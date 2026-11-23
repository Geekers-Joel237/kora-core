## What

<!-- What the change does, in a few lines. -->

## Why

<!-- The problem it solves, the issue it closes, or the ADR it implements. -->

## How it was checked

<!-- The test that started red, and how you verified the behaviour. -->

## Checklist

- [ ] The change started from a failing test, at the level that owns the behaviour
- [ ] `./gradlew build` is green: tests, architecture tests, API contract, coverage floors
- [ ] No committed contract file changed — or the change is intended and explained above
- [ ] No coverage floor was lowered — or the reason is explained above
- [ ] The invariants of `CLAUDE.md` §6 still hold
- [ ] A schema change comes with its `V<yyyyMMddHHmm>__snake_case.sql` migration
- [ ] The documentation the change makes wrong is updated
- [ ] A significant decision comes with its ADR
- [ ] Commits follow Conventional Commits; no secret, `.env` or personal data in the diff
