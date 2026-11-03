# HANDOFF_TO_CHATGPT

**Date:** 2026-11-03  
**From:** Cursor (executor)  
**MODE:** IMPLEMENTATION  
**Topic:** TH-402 G5 PR #16 remediation (D-AUTH-G5)

## Summary

PR #16 follow-up: G5-3 acceptance now requires measured **COMPLETED** TrialRecord (quiesced, TERMINATED, T_RUN_MAX, Oracle once, durable JSON match) in addition to preflight + paginated history. EXCLUDED is never VALID. Artifact remains separate; TrialRecord immutable.

| Finding | Remediation |
| --- | --- |
| EXCLUDED / incomplete trial treated as soft-ok | `G5AcceptanceEvaluator` — EXCLUDED / missing Oracle / wrong oracle count / missing durable JSON ⇒ INVALID |
| History+preflight alone | Insufficient; deprecated `fromValidation` always INVALID without trial outcome |
| DescribeTaskQueue | Focused unit tests via injectable client seam |

## Ask

Review whether DescribeTaskQueue + history gate is an acceptable exclusivity story given documented limitations (stale pollers; mid-trial poller appearance still caught by history gate).

## Still unauthorized

Live G5 · merge / auto-merge · EXP-* · #37 Done  
