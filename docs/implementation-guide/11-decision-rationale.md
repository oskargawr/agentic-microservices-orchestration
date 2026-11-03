# 11 — Decision and design rationale

Catalogue grounded in `coordination/DECISION_LOG.md` + as-built code.  
**Interpretation** labels mark commentary that is not a verbatim decision quote.

| Decision ID | Context | Chosen approach | Alternatives (documented only) | Implementation reference | Status |
| --- | --- | --- | --- | --- | --- |
| D-009 | MVP scope | Compare **A vs C** | B/RQ4 optional | Scope docs; only A built | Approved; C not started |
| D-011 | Oracle design | Option C independent classifier / four labels | — | `OutcomeClassifier`, `BusinessOutcome` | Approved / implemented |
| D-012 | Unresolved payment | `AMBIGUOUS`, not clean failure | — | classifier UNKNOWN/PENDING branches | Approved / implemented |
| D-201 | Variant A saga shape | reserve→charge→arrange→consume + compensation | — | `OrderFulfillmentWorkflowImpl` | Implemented |
| D-202-1 | Activity timeouts/retries | Shared `ActivityPolicies` | — | `ActivityPolicies.java` | Implemented |
| D-208 | Lost id recovery | Bounded single recovery POST | Nested retry storm avoided | `lostIdRecoveryOptions` | Implemented |
| D-R5 | F1 naming | Profile **F1-P** charge fail | Bare F1 rejected | `F1PFaultController`, `CanonicalFaultProfiles` | Implemented |
| D-AUTH-G5 | G5 lab | Implement opt-in G5-1…4; no live/merge at decision time | — | `g5/*`, PR #16 | Immutable; superseded in part by D-409 merge note |
| D-408 | S4-A/B/C | Merge matrix/gates on main | S4-D excluded | runner accept/matrix | Recorded |
| D-409 | Post PR #16 | G5 code on main; live still unauthorized; #37 not Done | — | coordination docs | Recorded |

## Plain-language “why” (interpretation + pointers)

### Why Temporal?
Durable orchestration and history for multi-service saga (**interpretation** of architecture choice; see Variant A as-built).  
**Code:** `orchestration/variant-a`.

### Why independent Oracle?
End-state success must not be “workflow completed” or LLM text (TH-011 / D-011).  
**Code:** `IndependentOracle`, `IndependenceTest`.

### Why explicit deterministic fault injection?
Reproducible cells in the matrix; verify fail-closed so “armed” means the service actually honors headers.  
**Code:** `FaultController.verify` + HTTP verifiers.

### Why F3-Unresolved separate gate?
`AMBIGUOUS` cell must not be silently accepted as ordinary failure/success matrix pass.  
**Code:** `ExpectedCellOutcome.requireF3UnresolvedGate`, `F3UnresolvedAcceptanceGateTest`.

### Why immutable TrialRecord?
Prevent silent overwrite of terminal evidence; duplicates excluded.  
**Code:** `FileResultStore`.

### Why G5-3 queue preflight + history validation?
Env flags alone do not prove exclusivity; history proves terminate path actually happened.  
**Code:** `G5IsolationPreflight`, `G5HistoryGate`.

### Why separate G5-2 and G5-3?
Worker presence vs absence changes Temporal behavior; one scenario cannot evidence both.  
**Code:** `G5ScenarioContractTest`, `G5OptIn`.

### Why terminate ≠ cancel?
R6 protocol uses Temporal **terminate** with reason `T_run_max`; cancel would be a different API/semantics.  
**Code:** `TrialRunner` terminate branch; `CountingVariantAdapter` never cancel.

### Why full observation window?
R14: allow service-side visibility before Oracle snapshot (`PROTOCOL_T_OBSERVE=30s`).  
**Code:** `RunnerTiming`, `TrialRunner` sleep before Oracle.

### Why CI ≠ live experimental evidence?
CI does not run opt-in G5 live wall-clock scenarios; EXP-* requires logged campaigns.  
**Code/docs:** `G5OptInCiSafetyTest`, `EXPERIMENT_LOG` empty of EXP-*.
