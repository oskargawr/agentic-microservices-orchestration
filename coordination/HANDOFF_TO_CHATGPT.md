# HANDOFF_TO_CHATGPT

**Data:** 2026-11-02  
**Od:** Cursor Agent  
**Tryb:** IMPLEMENTATION (limited follow-up to D-AUTH-1.1)  
**Task:** Fix JSON/CSV persistence semantics after independent audit NEEDS FIXES  
**Branch:** `feature/th-402-phase1-1-remediation` @ `3ea8d8e`  
**Prior audited HEAD:** `9dd7cd8`

---

## Root cause

`FileResultStore.saveTrial` treated JSON write + CSV rebuild as one atomic success. If JSON committed and CSV failed, `saveTrial` threw; `TrialRunner.persistCompletedOwnP1` then wrote `EXCLUDED/PERSISTENCE_FAILURE`, overwriting durable `COMPLETED`.

## Fix

1. **Canonical JSON** commit (with retries) alone determines `saveTrial` success.  
2. **CSV rebuild** is best-effort after JSON success; failure does not fail `saveTrial`.  
3. **Refuse downgrade** of durable `COMPLETED` to any non-COMPLETED status.  
4. `TrialRunner` re-reads durable JSON on persist error and returns COMPLETED if already committed.  
5. Malformed JSON during CSV rebuild fails closed (no skip).

## Tests

```
./gradlew :experiment:runner:test  → 28 tests, 0 fail, 0 skip
./gradlew test                     → BUILD SUCCESSFUL
```

| Test | Coverage |
| --- | --- |
| `csvFailureAfterJsonCommitDoesNotFailSaveOrDowngrade` | JSON stays COMPLETED; rebuild idempotent |
| `g8_csvFailureAfterJsonCommitKeepsCompletedWithoutRerun` | Runner path; no workflow/Oracle rerun |
| `g8_persistFailurePreservesOracleWithoutRerun` | partial content + durable ≠ false COMPLETED |
| `g1_…` | wrong-DB probe + business tables unchanged |
| `malformedCanonicalJsonFailsClosedDuringCsvRebuild` | fail-closed |

## Confirmations

- Durable COMPLETED **cannot** be downgraded by CSV failure.  
- Phase-1 commits unchanged. Design rev. 3.1 unchanged. OWN-P* unchanged.  
- G5 still pre-pilot. No push / PR / merge / Taiga change.  
- Working tree may still show unrelated `AGENTS.md`.  

## Next

**Independent read-only re-audit** required before any merge readiness claim. `NEXT_TASK=awaiting_owner` for that review.
