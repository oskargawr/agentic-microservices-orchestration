# Taiga Reconciliation Report — Full Project Audit

| Field | Value |
| --- | --- |
| **Date** | 2026-11-02 |
| **Project** | `agentic-orchestration-thesis` (Taiga project id `1`) |
| **Repo SoT** | `main` @ `31256cdc288aa071fa9f873a454afc2a1d91c46a` |
| **Mode** | Project-management sync only |
| **Prior related work** | `coordination/TH-207_TAIGA_RECONCILIATION.md` (created #63–#67) |

---

## 1. Audit scope

**GitHub:** merged PRs #1–#10; first-parent `main` history; `DECISION_LOG` / `PROJECT_STATE` / `NEXT_TASK`; architecture docs.  
**Taiga:** all user stories via paginated API (`page_size` — **56** stories, refs through **#67**).  
**Obsidian:** `Taiga.md`, Implementation Plan, Knowledge Systems notes, prior audit finding of “missing” #63–#67.

### Critical finding (pagination)

Earlier knowledge-sync and handoffs reported “30 stories / missing TH-205…” because the default Taiga list response was **truncated** (first page ≈ 30).  
**by_ref** and full pagination show **#63–#67 already exist and are Done** (created in TH-207).

---

## 2. Reconciliation matrix

| Technical ID | Taiga ref | Title (abbrev.) | Expected (repo) | Actual Taiga | Reconcile? | Evidence | Action taken |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TH-100 | **#20** | Repo + Temporal | Done | Done | No (links OK) | commit `c2a67c6` + TH-207 evidence | Unchanged |
| TH-101 | **#21** | Contracts | Done | Done | No | `664efae` / closure | Unchanged |
| TH-101.1 | **#63** | Contract hardening | Done | Done | No create | D-015; `53ce759`… | Unchanged (already Done) |
| TH-102 | **#22** | Inventory | Done | Done | No | PR #1 `b252675` | Unchanged |
| TH-103 | **#23** | Payment + F3 | Done | Done | No | PR #2 `5bdf809` | Unchanged |
| TH-104 | **#24** | Shipping | Done | Done | No | PR #3 `45849ab` | Unchanged |
| TH-200 | **#25** | Happy path A | Done | Done | No | PR #4 `9e4beff` | Unchanged |
| TH-201 | **#26** | Saga compensation | Done | Done | No | PR #6 `4814de6` | Unchanged |
| TH-202 | **#27** | Retries/timeouts | Done | Done | No | PR #8 `d33fc70` | Unchanged |
| TH-205 | **#64** | Console + worker | Done | Done | No create | PR #5 `1137fd1` | Unchanged |
| TH-206 | **#65** | Temporal E2E CI | Done | Done | No create | PR #7 `c1caa5b` | Unchanged |
| TH-207 | **#66** | Taiga reconciliation | Done | Done | Description append | `TH-207_TAIGA_RECONCILIATION.md` | **Updated** desc |
| TH-208 | **#67** | Lost-ID B+E | Done | Done (status); **stale desc** said “unapproved” | Yes | PR #9 `efb8c5c`; D-208 | **Updated** desc |
| TH-401 | **#36** | Business-state oracle | Done (401-A) | Done; weak 401-A evidence | Enrich | PR #10 `ab82fce` | **Updated** desc |
| TH-401-A | *(slice of #36)* | Independent Oracle Core | Done | Covered by #36 | **No separate US** | Same as TH-401-A impl | Documented only |
| TH-401-B | — | HTTP cross-check | Deferred | No story | None | Explicit deferral | **Not created** |
| TH-400 | **#35** | Fault profiles | Not impl | New | None | — | Unchanged |
| TH-402 | **#37** | Experiment runner | Design only / unapproved | New | None | design doc | Unchanged |
| TH-203 | **#28** | Fairness baseline | Not done | New | None | — | Unchanged |
| TH-204 | **#29** | Variant A README | Not done | New | None | — | Unchanged |
| TH-300–304 | **#30–34** | Variant C | Not started | New | None | — | Unchanged |
| TH-403 | **#38** | Reproducibility | Not done | New | None | — | Unchanged |
| TH-500–502 | **#39–41** | Pilot / freeze / confirmatory | Not done | New | None | — | Unchanged |
| TH-001…023 etc. | **#12–19** (+ others) | Org / lit / design | Mixed | Matches backlog | Spot-check OK | import | Unchanged this pass |

**Duplicates:** none for TH-101.1 / 205–208 (single US each). TH-401-A intentionally **not** duplicated as a second Done card.

**Story points:** #63–#67 remain **unset** (`null`) — not invented. Existing SP on #20–#41 preserved.

---

## 3. Changes applied (Stage 2)

### Created

**None** (missing historical stories already created in TH-207 as #63–#67).

### Updated

| Ref | Change |
| --- | --- |
| **#67** | Description rewritten to Done / D-208 / PR #9 / merge `efb8c5c` (removed stale “UNAPPROVED proposal” text). Status already Done — unchanged. |
| **#36** | Appended TH-401-A evidence block (PR #10, Option C, TH-401-B deferred). |
| **#66** | Appended follow-up note pointing to this report + pagination lesson. |

### Left unchanged

All other US including #20–#27, #63–#65, #35, #37, Variant C, lit TH-021+, OPTIONAL/OUT_OF_SCOPE backlog entries. **No deletions.** Estimates/priorities untouched.

---

## 4. Unresolved / Owner optional

1. **Refresh `infra/taiga-import/import_state.json`** if still stale vs live board (ops).  
2. **Set SP** on #63–#67 if Owner wants estimates (historical proposals: 101.1≈—, 205≈3, 206≈—, 208≈2 — **not applied**).  
3. **OUT_OF_SCOPE still “New”** — Archive only with Owner OK.  
4. **Native Taiga dependencies** still absent — deps remain in descriptions.  
5. Future API/scripts: always use **pagination** or `by_ref` when counting stories.

---

## 5. Obsidian sync

See updated vault notes: `Taiga.md`, `Implementation Plan.md`, Weekly Research Log (append), HANDOFF.

---

## 6. Confirmations

- No application code / protocol / Oracle / decision approval changes.  
- TH-400 / TH-402 / TH-401-B / Variant C **not** started or marked Done incorrectly.  
- `NEXT_TASK.md` approval status **unchanged**.  
- No commit / push / merge in this task.
