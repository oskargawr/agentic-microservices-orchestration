# PROJECT_STATE

**Last updated:** 2026-11-02  
**Updated by:** Cursor Agent (TH-400 S2 post-merge sync)  
**MODE this session:** IMPLEMENTATION (merge + coordination sync only)

## Current thesis scope (MVP)

| | |
| --- | --- |
| **Runner** | TH-402 phase-1 + 1.1 on `main` (PR #11) + F2 arming on `main` (PR #13) |
| **TH-400 S0** | **Owner-ratified (D-404)** — on `main` via PR #12 lineage |
| **TH-400 S1 (F1-P)** | **Merged** — PR [#12](https://github.com/oskargawr/agentic-microservices-orchestration/pull/12) · merge `c732558` |
| **TH-400 S2 (F2)** | **Merged** — PR [#13](https://github.com/oskargawr/agentic-microservices-orchestration/pull/13) · merge `b4ab3fa` · tip `17a3f79` |
| **TH-402 overall** | Phase-1 complete on main · ticket **NOT Done** |
| **Not authorized** | S3–S4 · Variant C · Taiga #35/#37 Done |

## Engineering

| | |
| --- | --- |
| **`origin/main`** | `b4ab3fa966b6d749530b7ace7feef1d84f5d46c6` |
| **S2 merge** | PR #13 → `b4ab3fa` (parents: `c732558` + `17a3f79`) |
| **S2 tip (ancestor)** | `17a3f791f45a31b2cc9d7016be68a785ab54d56e` |
| **Pre-merge audit** | PASS WITH LIMITATIONS (impl tip `70c4f85`; post-audit docs-only `17a3f79`) |
| **CI on merge tip** | build SUCCESS · GitGuardian SUCCESS |

## S2 on main (summary)

- `ErrorCode.ARRANGE_REJECTED`; ShippingFault + `thesis.shipping.fault-injection-enabled`
- Arrange: idempotency-first; F2 failure row HTTP 409; `noRollbackFor(ShippingException)`
- Orchestration: `arrangeFaultHint` forward + recovery
- Runner: `F2FaultController`, shipping preflight (409+code), adapter F2 → arrange hint; F0/F1-P unchanged
- No Oracle classifier / Payment production / F3 / Variant C changes

### Known limitations (carried)

- Preflight forge-URL class; probe residue until StateReset
- No live Temporal F1-P/F2 E2E in lab this session
- CLI does not exit non-zero solely on missing `CONFIRMED_FAILURE`
- Broad `noRollbackFor(ShippingException)` on arrange (safe today; future-hazard)

## Next three actions

1. Separate **D-AUTH-S3** before any F3 work (not started)  
2. Do **not** mark Taiga #35 / #37 Done  
3. Optional: live F2 E2E lab (does not replace D-AUTH)  
