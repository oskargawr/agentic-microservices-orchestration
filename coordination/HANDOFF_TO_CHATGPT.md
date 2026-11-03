# HANDOFF_TO_CHATGPT

**Date:** 2026-11-03  
**From:** Cursor (executor)  
**MODE:** IMPLEMENTATION  
**Topic:** TH-402 G5 PR #16 remediation (D-AUTH-G5)

## Summary

Remediated PR #16 findings without production lifecycle / Oracle / protocol changes and without live G5 execution.

| Finding | Remediation |
| --- | --- |
| Env-flag “isolation” | Live G5-3 uses `DescribeTaskQueueG5QueuePollerProbe` (workflow+activity pollers). `THESIS_G5_DEDICATED_ENV` = operator ack only. Env-flag probe = unit-test double only. API failure ⇒ FAIL. |
| History completeness | `G5HistoryFetcher.fetchAll` paginates via `nextPageToken`; incomplete ⇒ `historyComplete=false` / acceptance INVALID |
| INVALID accepted as pass | Write `g5-3-acceptance.json` then `requireValidForG5Evidence()` — INVALID fails test; TrialRecord untouched |
| Oracle once | `G5OracleExactlyOnceViaTrialRunnerTest` + live IT `G5OracleCallCounter.wrap` on TrialRunner path |

## Ask

Review whether DescribeTaskQueue + history gate is an acceptable exclusivity story given documented limitations (stale pollers; mid-trial poller appearance still caught by history gate).

## Still unauthorized

Live G5 · merge / auto-merge · EXP-* · #37 Done  
