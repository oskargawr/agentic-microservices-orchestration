# TH-402 — Readiness handoff dla Ownera

**Data:** 2026-11-02 (updated after Owner ratification)  
**Tryb:** documentation / coordination only  
**Werdykt analizy:** READY WITH CONDITIONS  
**Decision pack:** **D-403 APPROVED** · **D-AUTH NOT APPROVED**  
**Pełny raport (historyczny):** [`docs/architecture/TH_402_IMPLEMENTATION_READINESS.md`](../docs/architecture/TH_402_IMPLEMENTATION_READINESS.md)  
**Kanon decyzji:** [`DECISION_LOG.md`](DECISION_LOG.md) → **D-403**

---

## Status checklist

| ID | Resolution | Status |
| --- | --- | --- |
| **D-R2** | File-first JSON + CSV; Postgres deferred | **approved** |
| **D-R3** | Library + CLI; no Boot HTTP in MVP | **approved** |
| **D-R4** | Service-header faults (extend D-016); Toxiproxy deferred | **approved** |
| **D-R5** | Canonical F1 = **F1-P** charge-fail after HELD (not inventory reserve fail) | **approved** |
| **D-R7** | Serial trials + exclusive env lock | **approved** |
| **D-R8** | amountMinor/currency = experiment config; default 2599/PLN | **approved** |
| **D-R9** | Phase-1 before TH-400 Done (staged exception) | **approved** |
| **D-R10** | Skip incomplete & continue unless safety/quiesce/disarm/env block | **approved** |
| **D-R11** | TH-303 soft for A-only; full A×C needs C + TH-303 | **approved** |
| **D-R12** | Allowlist mandatory from first impl; separate confirmatory DB required before confirmatory | **approved** |
| **D-R13** | Quiesce wait configurable; default 5000 ms (not protocol constant) | **approved** |
| **D-SEQ** | Sequence **S1** | **approved** |
| **D-AUTH** | `NEXT_TASK=approved` for coding | **NOT approved** |

---

## Approved sequence S1

1. TH-402 **phase-1** — F0 × Variant A skeleton (F3 via D-016 optional, not mandatory acceptance)  
2. TH-400 — headers; F1-P, F2, F3  
3. TH-402 **phase-2** — full F0–F3  
4. Variant C + TH-303  
5. Pilot A×C → confirmatory (after separate confirmatory env + OWN-1)

### Acceptance

- **Phase-1 complete ≠** full TH-402 Done / #37 Done  
- **Full TH-402** still needs TH-400 + F0–F3 + design functions + C/TH-303 for A×C acceptance  

---

## Co nadal blokuje kod

Tylko **D-AUTH**: Owner musi wystawić osobne `NEXT_TASK=approved` z nazwanym slice (oczekiwane: phase-1).

D-402 + D-403 ≠ zgoda na implementację.

---

## Nadal otwarte (nie w D-403)

- OWN-1 confirmatory *n*  
- TH-401-B  
- Toxiproxy / Postgres results / dashboard  
- Exact confirmatory Compose/DB name (principle approved in D-R12; naming ops later)

---

## Potwierdzenia sesji zapisu

- Brak kodu / Gradle / testów / migracji / commit / push / PR  
- Kontrakt rev. 3.1 nietknięty (poza cross-ref w coordination)  
- Taiga #37: opis zaktualizowany; status **New**; **3 SP**; pełne zależności full-acceptance zachowane narracyjnie  
