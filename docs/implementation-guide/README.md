# Implementation Guide — Agentic Microservices Orchestration

**MODE:** DOCUMENTATION  
**Baseline:** `main` @ `0824c357265c17f2941d9f35b5295c14a51154a9`  
**PR #16 merge:** `801e343b36058094a91c5f89bcc57c945a860b0a` (ancestor of HEAD)  
**Post-merge CI (Owner-reported):** Run #71 SUCCESS on `801e343`  
**Scope:** Variant A + experiment harness + G5 opt-in lab (implementation). **Not** live G5, **not** Variant C, **not** EXP-*.

This handbook is the human-readable map from thesis concepts to code, tests, and evidence rules. The repository remains the technical source of truth; Obsidian is the navigable learning layer.

## Learning path

1. [01 Research context](01-research-context.md) — what the thesis investigates  
2. [02 System architecture](02-system-architecture.md) — components and Mermaid overview  
3. [03 Repository map](03-repository-map.md) — modules, classes, concept → file  
4. [07 Temporal and workflows](07-temporal-and-workflows.md) — saga, retries, compensation  
5. [08 Runner and Oracle](08-runner-and-oracle.md) — trial lifecycle, classification  
6. [05 Fault profiles](05-fault-profiles.md) — F0…F3 matrix  
7. [04 Runtime lifecycle](04-runtime-lifecycle.md) — sequence diagrams  
8. [06 Experimental phases](06-experimental-phases.md) — S0–S4 vs G5 vs EXP-*  
9. [10 G5 live readiness](10-g5-live-readiness.md) — pre-pilot gate  
10. [09 Test catalogue](09-test-catalogue.md) — what each test proves / does not prove  
11. [11 Decision rationale](11-decision-rationale.md) — why key design choices exist  
12. [12 Glossary](12-glossary.md) — acronyms and identifiers  
13. [13 Supervisor questions](13-supervisor-questions.md) — oral defence prep  

## Status legend (use everywhere)

| Marker | Meaning |
| --- | --- |
| **implemented** | Code exists on `main` |
| **merged** | Landed via PR |
| **CI-verified** | Automated CI green (not live Temporal evidence) |
| **live-tested** | Opt-in lab against real Temporal ran with artifacts |
| **experimentally measured** | EXP-* campaign recorded in `EXPERIMENT_LOG` |
| **deferred / unauthorized** | Explicitly out of current Owner auth |

**Current facts:** G5 implementation = **merged + CI-verified**. G5 live = **not** live-tested. EXP-* = **none**. Taiga #37 = **not Done**. Variant C = **not implemented**.

## Related in-repo docs

- [`docs/architecture/`](../architecture/) — contracts-era overview (partially stale; prefer this guide for as-built)  
- [`docs/protocol/`](../protocol/) — business invariants / oracle protocol  
- [`coordination/`](../../coordination/) — PROJECT_STATE, NEXT_TASK, DECISION_LOG, EXPERIMENT_LOG  
- [`coordination/TH_402_G5_DISCOVERY.md`](../../coordination/TH_402_G5_DISCOVERY.md) — G5 requirements SoT  

## Discrepancy notes

| Document | Issue | Authoritative source |
| --- | --- | --- |
| `docs/architecture/OVERVIEW.md` | TH-101 wording (“services not implemented”) | this guide + Java under `services/`, `orchestration/`, `experiment/` |
| Root `README.md` status table | Still lists Shipping / Temporal A / runner as “Planned” | `settings.gradle.kts` + as-built modules (audited 2026-11-03) |
| Activities implementation class | Prefer `HttpOrderFulfillmentActivities` (not a `*ActivitiesImpl` name) | `orchestration/variant-a/.../HttpOrderFulfillmentActivities.java` |
