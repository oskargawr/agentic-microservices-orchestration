# EXPERIMENT_LOG

Index of experiment runs. **Not** a substitute for raw data.

Raw outputs must live in the future code/data repository (immutable). Vault links document meaning.

**Rule:** Never invent results. Never overwrite raw artifacts. Exploratory ≠ confirmatory.

## Runs

| Exp ID | Date | Protocol ver. | Code commit | Variant | Scenario | Model / config | Artifact path | Status | Integrity notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| — | — | — | — | — | — | — | — | **none yet** | Harness F0–F3 on `main` (merge `bcc38c7`) exists; **no** protocol pilot/confirmatory trial artifacts recorded yet |

## Harness readiness note (not a run)

| Item | Fact |
| --- | --- |
| TH-400 S0–S3 | On `main` @ `bcc38c7` (PR #14 merge) |
| Profiles | F0, F1-P, F2, F3-Recovered, F3-Unresolved (Variant A) |
| Live Temporal lab E2E for F1–F3 | **Not** performed (S3-E optional; G5 pre-pilot still open for full runner) |
| This log | Remains empty of EXP-* until an actual harness experiment is executed and archived |

## Protocol versions (index)

| Protocol ver. | Date | Notes | Vault / file |
| --- | --- | --- | --- |
| (none frozen) | — | Draft methodology only | Obsidian `Experimental Protocol.md`, `Research Methodology.md` |

## When adding a run

1. Assign `EXP-YYYYMMDD-NNN`
2. Record commit hash, protocol version, variant (A|C|…), fault scenario ID
3. Point to absolute or repo-relative path of raw JSON/logs
4. Mark `exploratory` or `confirmatory`
5. Note missing metrics / failed runs (do not drop them)
