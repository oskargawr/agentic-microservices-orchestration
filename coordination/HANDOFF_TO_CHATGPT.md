# HANDOFF_TO_CHATGPT

**Data:** 2026-11-02  
**Od:** Cursor Agent  
**Tryb:** IMPLEMENTATION (D-AUTH-S2)  
**Task:** TH-400 S2 F2 shipping fault injection — local complete  
**Base `origin/main`:** `c73255842d771126153dba94d6bcf1031b1d072b`  
**Branch:** `feature/th-400-s2-f2` (local commits; **no push/PR**)

---

## Authorization

| | |
| --- | --- |
| D-AUTH-S2 | **GRANTED** — Owner „zatwierdzam” |
| Push / PR / merge | **NOT** authorized |
| TH-400 / #35 Done | **NOT** marked |

## What shipped (local)

| Area | Change |
| --- | --- |
| Contracts | `ErrorCode.ARRANGE_REJECTED` |
| Shipping | Header injection; failure idempotency before injection; `noRollbackFor` commit of 409 failure; IT cases |
| Orchestration A | `arrangeFaultHint` on input/activities/HTTP/workflow + recovery; CompIT `t400_s2_*`; recovery hint unit test |
| Runner | `F2FaultController` + shipping preflight + CLI `--shipping-base-url` + adapter mapping; F0/F1-P preserved |

## Tests

Mandatory modules (contracts, runner, shipping, variant-a): **133 / 0 / 0 / 0**.  
Also: payment `confirmedFailure` PASS; console compiles.

## Explicit non-goals (honored)

Oracle classifier · F3 · Variant C · Payment production · S0 rewrites · Taiga Done · remote push

## Ask for ChatGPT / Owner

1. Review S2 local commit(s) / optional audit before push.  
2. If OK: authorize push + PR (no merge until review).  
3. S3 remains blocked without separate D-AUTH.
