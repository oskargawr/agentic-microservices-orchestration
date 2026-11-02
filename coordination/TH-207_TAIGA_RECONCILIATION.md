# TH-207 — Taiga backlog reconciliation report

**Date:** 2026-11-02  
**Taiga:** http://localhost:9000/project/agentic-orchestration-thesis (project id `1`)  
**Repo SoT:** `main` @ `06166d1` (post TH-202 closure)  
**Mode:** Coordination only — no application code changes

---

## 1. Summary

| Action | Count |
| --- | ---: |
| User stories **updated** (status and/or description) | **10** |
| User stories **created** | **5** |
| Left unchanged (other backlog tickets) | **46** original import stories not requiring status change for this audit |
| Tickets deleted | **0** |
| Estimates / owners invented | **0** |

**Access:** Taiga API reachable; updates applied successfully.

---

## 2. Completed tasks — TH ID → Taiga

| TH ID | Taiga ref | Status after audit | Evidence |
| --- | --- | --- | --- |
| TH-100 | #20 | Done (enriched) | commit `c2a67c6` |
| TH-101 | #21 | Done (enriched) | `664efae` / closure `40cc40d` |
| TH-101.1 | **#63** (created) | Done | D-015; commits `53ce759`, `31f0130`, `ec89378` |
| TH-102 | #22 | Done (enriched) | PR #1 `b252675` |
| TH-103 | #23 | Done (enriched) | PR #2 `5bdf809` |
| TH-104 | #24 | **New → Done** | PR #3 `45849ab` |
| TH-200 | #25 | **New → Done** | PR #4 `9e4beff` |
| TH-201 | #26 | **New → Done** | PR #6 `4814de6` |
| TH-202 | #27 | **New → Done** | PR #8 `d33fc70` |
| TH-205 | **#64** (created) | Done | PR #5 `1137fd1` |
| TH-206 | **#65** (created) | Done | PR #7 `c1caa5b` |
| TH-207 | **#66** (created) | Done | this reconciliation |

Descriptions retain original import text; an **Implementation evidence (TH-207 reconciliation)** block was appended (not destructive rewrite of history).

---

## 3. Missing tickets created

| Ref | Subject | Rationale |
| --- | --- | --- |
| #63 | [TH-101.1] Contract hardening… | Completed in repo (D-015) but absent from Taiga import |
| #64 | [TH-205] Experiment Console… | Completed PR #5; missing from import |
| #65 | [TH-206] Temporal E2E in CI… | Completed PR #7; missing from import |
| #66 | [TH-207] Taiga backlog reconciliation | Owner-authorized coordination task |
| #67 | [TH-208] F3 lost-response… **(proposed)** | D-202-3 deferred gap; **New / unapproved** |

Epic links: created stories attached to P3 (101.1, 205–207) or P5 (208).

---

## 4. Future work — approved vs proposed

### Approved in decisions / closed implementation

- D-201, D-202-1…5, D-015, D-016, protocol-v0 — **not** open tickets for re-decision.

### In Taiga as New (backlog candidates — **not** Owner-approved as NEXT_TASK)

| Area | Tickets | Note |
| --- | --- | --- |
| Variant A remaining | TH-203 #28, TH-204 #29 | TH-203 explicitly annotated: **not** approved next |
| Variant C | TH-300–304 #30–34 | CORE planned; unapproved to start |
| Experiment infra | TH-400–403 #35–38 | Oracle, runner, F0–F3 profiles |
| Pilot / confirmatory | TH-500–503 | |
| Writing / defense | TH-600–702 | TH-600 In progress |
| OPTIONAL | TH-B*, TH-O* | |
| OUT_OF_SCOPE | TH-X* | |

### Proposed / deferred (not approved)

| ID | Item | Source |
| --- | --- | --- |
| TH-208 #67 | Lost-response-without-returned-ID | D-202-3 |
| (note on TH-400) | Full F0–F3 harness remains TH-400 | protocol + D-201 U-201-4 |
| — | Fairness A↔C experimental conditions | TH-203 / TH-303 / harness — still candidate |

`NEXT_TASK.md`: **no approved next task** (unchanged approval status).

---

## 5. Dependencies

| Relationship | Representation |
| --- | --- |
| Native Taiga dependency field | **None** (documented in story descriptions only — known import limitation) |
| TH-205 → TH-200; TH-206 → TH-205; TH-201 → TH-200; TH-202 → TH-200 | Documented in descriptions / evidence blocks |
| TH-208 ↔ TH-400 / F3 | Cross-noted on TH-400 and TH-208 |

**Issue for Owner:** if native blockers are desired, enable a plugin or accept description-only deps.

---

## 6. Issues requiring Owner decisions

1. **TH-203 vs TH-204 vs TH-300 vs TH-400 vs TH-208** — which becomes next approved `NEXT_TASK`? (Cursor recommendation below; **not** approved here.)
2. **TH-208 vs absorb into TH-400** — keep separate proposed ticket or merge scope into fault-profile work?
3. **Story points on created tickets** — left unset/default; Owner may set SP if desired (not invented).
4. **OUT_OF_SCOPE tickets still “New”** — consider moving to Archived for board hygiene (not done; needs Owner OK).
5. **Duplicates:** none found for TH-205/206 after create; original 51 + 5 = 56 stories. No deletions.
6. **import_state.json** in `infra/taiga-import/` is stale vs live Taiga — refresh optional ops task.

---

## 7. Recommended next task (not approved)

**Recommendation:** **TH-203** — Fairness baseline tests (idempotency, partial completion) for Variant A, **or** **TH-204** ADR/README if documentation debt is preferred before more tests.

**Rationale:** Completes Variant A CORE slice before Variant C (TH-300+) and before heavy harness (TH-400+). TH-208 should wait until Owner sequences it relative to TH-400 F3.

**Do not treat this recommendation as Owner approval.**

---

## 8. NEXT_TASK / PROJECT_STATE

- `NEXT_TASK.md` approval status **unchanged** (still awaiting Owner next approval after TH-202).
- Optional audit note may be appended without designating a new active task.
