# DECISION_LOG

Canonical research narrative also lives in Obsidian  
`Praca magisterska - Orkiestracja agentowa/09 - Project Management/Decision Log.md`.  
**This file** is the handoff-oriented log with explicit `proposed | approved | rejected | superseded` and decision-maker.

**Rule:** Never set status to `approved` without Project Owner explicit instruction.

---

## D-COORD-001 — Three-party file-based coordination

| Field | Value |
| --- | --- |
| **Date** | 2026-10-22 |
| **Status** | approved |
| **Decision-maker** | Project Owner (task request to set up workflow) |
| **Context** | Need reliable handoffs among Owner, ChatGPT advisor, Cursor executor without custom APIs |
| **Alternatives** | Custom bot/API; ChatGPT-only; Cursor-only notes |
| **Recommendation** | Markdown under `coordination/` + existing Git when available |
| **Decision** | Adopt `coordination/` files and protocol in README |
| **Evidence** | Owner brief “Set Up a Three-Party Thesis Workflow” |
| **Consequences** | Cursor starts/ends substantial thesis work via these files; no auto-approval of advisor advice |
| **Related** | Operating Protocol (vault); AGENTS.md |

---

## D-009 — MVP-first scope: A vs C mandatory

| Field | Value |
| --- | --- |
| **Date** | 2026-10-22 (proposed) · **2026-10-23 (Owner approved)** |
| **Status** | **approved** (Owner) |
| **Decision-maker** | Project Owner |
| **Supervisor** | Expressed support for the **overall topic and research idea**; **no objections** raised. This is **not** documented as explicit approval of every methodological detail (metrics, fault set, model choice, n, etc.). |
| **Context** | Lit. review showed B/RQ4 inflate risk; fair comparison needs shared services + end-state oracle |
| **Alternatives** | Full A/B/C mandatory; A vs B only; A vs C |
| **Recommendation** | Mandatory A vs C; B and RQ4 OPTIONAL after MVP Complete |
| **Decision** | **Proceed with mandatory MVP = A vs C**; Variant B and RQ4 remain OPTIONAL; OUT_OF_SCOPE list retained |
| **Evidence** | Owner instruction 2026-10-23 (“Supervisor Alignment… proceed with A vs C”); prior lit. synthesis 2026-10-22; vault meeting note |
| **Consequences** | No CORE tickets for B/RQ4; MVP gate excludes them; unblocks repo bootstrap and TH-011+ |
| **Related RQ / tickets** | RQ1–3; TH-010 (completed); Backlog CORE/OPTIONAL; TH-011 next |

---

## D-008 — Research question priority

| Field | Value |
| --- | --- |
| **Date** | 2026-10-22 (proposed) · **2026-10-23 (Owner approved, aligned with D-009)** |
| **Status** | **approved** (Owner) |
| **Decision-maker** | Project Owner |
| **Supervisor** | Topic/idea support only — not a line-by-line RQ ratification |
| **Context** | Four RQs not equally feasible in two semesters |
| **Recommendation** | RQ1+RQ2 main; RQ3 secondary metrics; RQ4 exploratory/optional |
| **Decision** | RQ1+RQ2 primary; RQ3 secondary; RQ4 optional / out of mandatory MVP |
| **Related** | D-009; TH-010; Research Questions.md |

---

## D-015 — TH-101.1 contract hardening (idempotency, resolve, OpenAPI split)

| Field | Value |
| --- | --- |
| **Date** | 2026-11-01 |
| **Status** | **approved** (Owner TH-101.1 brief) |
| **Decision-maker** | Project Owner |
| **Context** | Harden contracts before TH-102 |
| **Decision** | Header-only `Idempotency-Key` (same key+payload replay; conflict → 409); payment resolve rules (inconclusive keeps UNKNOWN/PENDING); per-service OpenAPI + shared `components.yaml`; stable `ErrorCode`s; charge amount must be strictly positive |
| **Confirmed** | C-101-1, C-101-2, C-101-3 (see D-014) |
| **Related** | TH-101.1 · TH-102 next |

---

## D-014 — TH-101 shared domain contracts

| Field | Value |
| --- | --- |
| **Date** | 2026-11-01 |
| **Status** | **approved** (Owner tasked TH-101 execution; C-101-1..3 confirmed 2026-11-01) |
| **Decision-maker** | Project Owner |
| **Context** | Lock identical API/state surface for A and C before service implementation |
| **Decision** | Add Gradle module `:contracts` (DTOs, enums, transition guards, idempotency helpers) + OpenAPI + state diagrams; services depend on module; **no** HTTP business endpoints, persistence, Temporal, LLM, or runner in this ticket |
| **Confirmed (Owner)** | **C-101-1** orchestration calls inventory `consume` after confirmed `arrange`; **C-101-2** money as `amountMinor`+currency; **C-101-3** HTTP prefix `/api/v1` |
| **Evidence** | `:contracts` tests green; `./gradlew build` SUCCESS; branch `feature/th-101-domain-contracts` (+ TH-101.1 hardening) |
| **Related** | TH-101 · TH-101.1 · TH-011 · TH-012 · next TH-102..104 |

---

## D-013 — TH-100 infra bootstrap (Postgres schemas + Temporal server)

| Field | Value |
| --- | --- |
| **Date** | 2026-11-01 |
| **Status** | **approved** (Owner brief) |
| **Decision-maker** | Project Owner |
| **Context** | Reconcile backlog TH-100 (monorepo + Temporal local + build) with Owner request (Postgres schema-per-service + build/run; no domain/workflows/LLM/runner) |
| **Decision** | Extend existing `agentic-orchestration-thesis/` Git scaffold; Gradle multi-module stubs; Compose Postgres on **5433** with schemas `inventory`/`payment`/`shipping`; Temporal auto-setup on **7233**; no business APIs or workflows |
| **Also confirmed** | OWN-3 CONSUMED; OWN-4 timing provisional; F3 mandatory (doc); A-vs-C MVP unchanged |
| **Evidence** | `./gradlew build`; `scripts/verify-infra.sh`; inventory `/actuator/health` UP with DB |
| **Related** | TH-100 · TH-101+ next for contracts/services |

---

## D-012 — Experimental protocol v0 + OQ-1..3 resolutions (TH-012)

| Field | Value |
| --- | --- |
| **Date** | 2026-10-24 |
| **Status** | **approved** (Owner tasked TH-012 execution; design draft freeze pending OWN-3/4 veto) |
| **Decision-maker** | Project Owner (task) · Cursor documented |
| **OQ-1** | Success path: reservation `HELD` → `CONSUMED` on arrange; stock `onHand`/`held` decremented |
| **OQ-2** | Fault F3 ambiguous payment mandatory; timeout ≠ failure; **no refund unless CAPTURED confirmed** |
| **OQ-3** | `T_observe=30s`, `T_run_max=120s`; unresolved UNKNOWN → oracle `AMBIGUOUS` (RQ2 ambiguous, not INVALID) |
| **DB** | PostgreSQL planned for local services |
| **Evidence** | `docs/protocol/EXPERIMENTAL_PROTOCOL_V0.md` + amended invariants doc |
| **Open for Owner** | Confirmatory *n* (OWN-1); model (OWN-2); veto windows on OWN-3/4 |
| **Related** | TH-012 done · TH-400 · TH-501 · RQ1–RQ3 |

---

## D-011 — Business invariants + DB-primary oracle (TH-011)

| Field | Value |
| --- | --- |
| **Date** | 2026-10-24 |
| **Status** | **approved** (Owner executed TH-011 task; design freeze for drafting) |
| **Decision-maker** | Project Owner (task execution) · Cursor documented |
| **Context** | Need shared business correctness for fair A vs C; LLM text must not define success |
| **Alternatives** | API-only oracle; DB-only; hybrid; treat timeout as failure |
| **Decision** | Minimal order process (reserve→pay→ship); outcomes `CONFIRMED_SUCCESS` / `CONFIRMED_FAILURE` / `AMBIGUOUS` / `INVALID`; invariants I1–I7; timeout ≠ failure; payment `UNKNOWN` explicit; **primary oracle = local DB**, optional API cross-check |
| **Evidence** | Spec `agentic-orchestration-thesis/docs/protocol/BUSINESS_INVARIANTS_AND_ORACLE.md` |
| **Open** | OQ-1..OQ-3 in spec (consume-on-ship; UNKNOWN in fault set; harness wait T) |
| **Related** | TH-011 done · TH-012 · TH-401 · RQ1–RQ2 |

---

## D-010 — Thesis application monorepo root

| Field | Value |
| --- | --- |
| **Date** | 2026-10-23 |
| **Status** | **approved** |
| **Decision-maker** | Project Owner (bootstrap task) · Cursor executed |
| **Context** | Workspace `cursor_space` mixes ZPR materials, Obsidian vault, Taiga infra — unsuitable as a single app Git root |
| **Alternatives** | Git at `cursor_space/`; nested app under `infra/`; separate directory |
| **Decision** | Initialize Git monorepo at `agentic-orchestration-thesis/` (sibling to `coordination/`, `infra/`, vault) |
| **Evidence** | Ambiguity rule in Owner bootstrap brief; inspection of nested contents |
| **Consequences** | Application code/history lives only under that tree; vault/Taiga/coordination stay outside app repo |
| **Related** | TH-100 (Temporal/services still future); TH-010 complete |

---

## Earlier decisions (mirrored; see vault for narrative)

| ID | Summary | Status | Decision-maker |
| --- | --- | --- | --- |
| D-001 | Working title PL/EN unchanged | proposed → treat as accepted for now (supervisor liked topic) | Owner / supervisor topic support |
| D-002 | Knowledge base in Obsidian vault beside ZPR | approved | Owner (setup brief) |
| D-003 | No full app in initial setup session | approved / satisfied | Owner (setup brief) |
| D-004 | Candidate A/B/C with possible narrowing | superseded by D-009 | — |
| D-005 | Prefer Java / Spring Boot / Temporal / local LLM | **proposed** (stack not locked by this session) | — |
| D-006 | Citation style after university requirements | approved | Owner (setup brief) |
| D-007 | Operating Protocol for agent sessions | approved | Owner (protocol brief) |

---

## Template for new entries

```markdown
## D-XXX — Title

| Field | Value |
| --- | --- |
| Date | YYYY-MM-DD |
| Status | proposed \| approved \| rejected \| superseded |
| Decision-maker | Owner / — |
| Context | |
| Alternatives | |
| Recommendation | |
| Decision | |
| Rationale / evidence | |
| Consequences | |
| Related RQ / tickets | |
```
