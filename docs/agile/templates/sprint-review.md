# Sprint NN — Review · <yyyy-mm-dd>

**Sprint goal**: <goal> → **Met | Not met** — <one sentence>

## Items
| ID | Product | Maker | Code checker | QA acceptance | DoD failures | PO decision |
|---|---|---|---|---|---|---|
| KC-<n> | API · mobile | | Pass · Fail (who) | Accepted · Rejected (n/m scenarios) | | Accepted · Rejected · Accepted over a Fail or Rejected (reason) |

## Demo script
```bash
# replayable steps: curl calls or app path
```

## Increment checks (DoD Level 2)
| Check | Command | Result |
|---|---|---|
| API CI on `develop` | `./gradlew build` | |
| Acceptance suite | `./gradlew acceptanceTest` (from KC-47) | |
| Mobile CI on `develop` | `npm run verify` | |

## Measures
| Throughput | Cycle time (median) | Review wait (median) | Checker fails | QA rejections | Overrides | DoD failures | Escaped defects | Ceremony share | Plan usage per accepted item | Usage-limit hits |
|---|---|---|---|---|---|---|---|---|---|---|

## Backlog and roadmap changes
-
