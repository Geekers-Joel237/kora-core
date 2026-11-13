---
name: dod-check
description: Check the current branch against the KORA Core Definition of Done (docs/agile/definition-of-done.md) and produce the checklist to paste in the pull request. Use when the user runs /dod-check, before opening a PR, or asks whether a story is done.
---

# /dod-check

Run by the maker before opening the PR, and again by the checker in a fresh session
(`docs/agile/README.md` §6). As checker, work from the PR diff and the refinement record
only — never from the maker's conversation — and end with **Pass** or **Fail**.

1. Identify the item from the branch name (`feature/KC-<n>-…`, `hotfix/KC-<n>-…`). No ID
   in the branch name is itself a failure of D-31.
2. Compute the diff against the base (`develop` for feature, `main` for hotfix).
3. Run what can be run, and report real output only:
   - `./gradlew build` (and `jacocoTestReport` / coverage verification once KC-21 lands)
   - `npm run verify` and `npm run audit` in `mobile/` if the diff touches `mobile/` (mobile CI, D-35)
   - commit lint: every commit matches `type(scope): subject` and carries `Refs: KC-<n>`
4. Check the review lines against the diff and the refinement record
   `docs/agile/refinements/KC-<n>.md`: scenario files untouched by the maker (D-2); reproduction test first for a
   fix; `CLAUDE.md` §7 rules; docs moved with the code; flag decision honoured; contract
   docs updated if an endpoint changed; every signal of the record's observability table
   emitted (D-24 to D-27).
5. Output one Markdown checklist, Level 1 lines only (plus mobile lines when relevant).
   Section A (feature acceptance) is QA's: mark D-1 and D-3 `see /accept`, check only D-2.
   `- [x] D-8 …` with the evidence in a few words, `- [ ] D-n …` with what is missing,
   and `n/a` with the reason. Status 🔧 lines whose enabler is not delivered are marked
   `pending KC-<m>`, not passed.
6. End with **Done** or **Not done** (as maker) or **Pass** or **Fail** (as checker), and
   the shortest list of what blocks it. Never round a failure up. Flag when the item's
   security exposure is material: D-34 then also needs a human reading of the diff.
