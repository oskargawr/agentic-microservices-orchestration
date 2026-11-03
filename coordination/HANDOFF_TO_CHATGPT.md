# HANDOFF_TO_CHATGPT

**Data:** 2026-11-03  
**Od:** Cursor Agent  
**Tryb:** IMPLEMENTATION — TH-400 S4-A/B/C (D-AUTH-S4); merge **NOT** authorized  
**Branch:** `feature/th-400-s4-matrix-readiness`

---

## Authorization

| | |
| --- | --- |
| Decision | **D-AUTH-S4** approved |
| Authorized | S4-A · S4-B · S4-C |
| Deferred / excluded | **S4-D** · Variant C · Oracle changes · protocol timings · #68 · #35/#37 Done · **merge** |

## Implementation summary

### S4-A
- `CompositeFaultController.variantAAllProfiles` — concurrent F0…F3-Unresolved registration (order-preserving)
- `CanonicalFaultProfiles` + `MatrixExperimentDriver` — serial schedule × `n` under existing `TrialRunner` lifecycle
- CLI: `--fault-profiles=` / repeatable `--fault-profile=` + `--n=`

### S4-B
- `ExpectedCellOutcome` / `CellOutcomeValidation` / `MatrixSummary` (SUCCESS ≡ `CONFIRMED_SUCCESS`)
- Durable `matrix-summary.json`; CLI exit `0/1/3` (+130 abort)
- F3-Unresolved gate unchanged inside `TrialRunner`

### S4-C
- `docs/architecture/TH_400_EVIDENCE_AND_LIMITATIONS.md`
- Quantitative retry: forward `maxAttempts=5` + recovery `1` → **6** charge invocations on exhaustion (`OrderFulfillmentRetryTimeoutTest`)

## Tests (this branch)

```text
java -jar gradle/wrapper/gradle-wrapper.jar \
  :orchestration:variant-a:test :experiment:runner:test :services:payment:test --rerun-tasks
→ BUILD SUCCESSFUL
variant-a 39 / 0 / 0
runner 85 / 0 / 0
payment 14 / 0 / 0
```

Layers: unit + CompIT (`TestWorkflowEnvironment`) + mocked lifecycle — **no** live Temporal (S4-D not done).

## Remaining limitations

See evidence doc: L-S3E, L-503-NEST, L-PROBE, L-SHIP-NR, L-EXP, L-AXC. L-CLI-ORACLE mitigated by S4-B. L-RETRY-Q partially closed by one bounded measurement.

## Ask

Owner: review PR → separate **merge** auth. Do not mark #35/#37 Done.
