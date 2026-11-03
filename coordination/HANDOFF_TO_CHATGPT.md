# HANDOFF_TO_CHATGPT

**Data:** 2026-11-03  
**Od:** Cursor Agent  
**Tryb:** IMPLEMENTATION — D-AUTH-S3 (F3-Recovered + F3-Unresolved)  
**Branch:** `feature/th-400-s3-f3`  
**HEAD:** `ba81686eafeba0db1b57594a1ecef45e8250d329`  
**Base:** `c16e1f0` (local) / `origin/main` `f3ac351`  
**Push / PR / merge:** **NOT performed / NOT authorized**

---

## Summary

Owner granted **D-AUTH-S3**. Dual F3 profiles implemented end-to-end (Payment opacity, Variant A getPayment hint + compensation leave-dangling for Unresolved, Runner controllers + harness gate). Oracle API/classifier **unchanged**.

## Evidence (tests)

```text
./gradlew :services:payment:test :contracts:test \
  :experiment:runner:test :experiment:oracle:test \
  :orchestration:variant-a:test
→ BUILD SUCCESSFUL
```

- Payment IT: opacity 503 + no DB mutation; unhinted CAPTURED (D-016)  
- CompIT: `f3Recovered_*` SUCCESS; `f3Unresolved_*` AMBIGUOUS + CAPTURED/HELD + no refund/arrange  
- Runner unit: F3 controllers + acceptance gate + adapter hints  
- Regressions: F0/F1-P/F2 paths in same suites green  

**S3-E live Temporal E2E lab:** **not** run (optional 1 SP).

## Key design (Source fact)

| Topic | Behavior |
| --- | --- |
| Opacity | `RESOLVE_TRANSPORT_OPAQUE` → HTTP 503; header-presence; no shared counter |
| getPayment | activity takes `faultHint`; experimental path uses resolve hint; compensation/refresh unhinted except Unresolved leave-dangling |
| Unresolved compensation | When AMBIGUOUS + opacity hint + in-memory UNKNOWN → skip refund **and** release (CAPTURED+HELD) |
| Harness gate | profile + Oracle AMBIGUOUS + DB CAPTURED/HELD/no ARRANGED; no Oracle API change |

## Limitations / audit notes

- Unresolved CompIT uses `resolveAttemptLimit=1` (Temporal still retries each activity ×5).  
- Preflight creates probe payments (same class as F1-P residue).  
- Self-audit only — Owner independent audit still required before publication auth.  
- Local `c16e1f0` EOD docs commit remains unpushed; S3 commits are branch-local.

## Taiga / Obsidian

- #35 In Progress — S3 notes + **9 SP** estimate recorded (≠ Done)  
- #37 In Progress unchanged  
- #68 New unchanged  
- Vault Current Status / Weekly Log updated  

## Ask

1. Owner audit → then separate push/PR/merge auth.  
2. Do not mark #35/#37 Done.  
