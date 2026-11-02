# HANDOFF_TO_CHATGPT

**Data:** 2026-11-02  
**Od:** Cursor Agent  
**Tryb:** IMPLEMENTATION (TH-401-A Owner review follow-up)  
**PR:** https://github.com/oskargawr/agentic-microservices-orchestration/pull/10  
**HEAD:** `df245baf88552efc9fa6c47cbeda18da1bb5ab5b`  
**Nie zmergowano.** TH-401-B nie rozpoczęte.

---

## Findings addressed

### 1. Blocking — missing stock → never CONFIRMED_SUCCESS

| Decyzja z protokołu | Uzasadnienie |
| --- | --- |
| Brak wiersza `stock` przy CONSUMED+CAPTURED+ARRANGED → **`AMBIGUOUS`** + `pattern=NONE` + reason **`MISSING_STOCK_EVIDENCE`** | TH-011 §2.1 wymaga stock w success snapshot; I1 Fail dotyczy ujemnych/mismatch liczb, nie braku wiersza → brak dowodu ≠ udowodnione naruszenie → nie `INVALID`, nie `CONFIRMED_SUCCESS` |
| `isConfirmedSuccess` wymaga obecnego stock + spójności I1 (`onHand≥held≥0`, `held==sum(HELD)`) | |
| Absolute „onHand reduced by qty” | **Nie weryfikowane** bez baseline przed runem; na SUCCESS dodawany warning `STOCK_ONHAND_DECREMENT_NOT_VERIFIED_WITHOUT_BASELINE` |

### 2. I7 audit

Dokument: `experiment/oracle/docs/I7_ENFORCEMENT.md`  
Oracle nie twierdzi pełnego pokrycia I7 z terminalnego snapshotu; logi nie są autorytatywne. Unikalność payment/shipment = schemat + testy usług; multi-HELD / multi-reservation = INVALID w oracle.

---

## Pliki zmienione (follow-up)

- `experiment/oracle/.../OutcomeClassifier.java`
- `experiment/oracle/.../OracleReasonCode.java` (`MISSING_STOCK_EVIDENCE`)
- `experiment/oracle/.../ScenarioClassificationTest.java` (+ regresja missing stock)
- `experiment/oracle/.../support/SnapshotFixtures.java`
- `experiment/oracle/docs/I7_ENFORCEMENT.md` *(nowy)*
- `coordination/HANDOFF_TO_CHATGPT.md`

---

## Testy

| Command | Result |
| --- | --- |
| `./gradlew --no-parallel :experiment:oracle:test` | SUCCESS — **28** tests, 0 skipped |
| `./gradlew --no-parallel cleanTest test` | SUCCESS |
| `./gradlew --no-parallel build` | SUCCESS |
| Aggregate XML | **130** / 0 skipped / 0 fail / 0 err |

---

## Remaining limitations

- Absolute `onHand` Δ vs pre-run baseline nadal poza oracle (ostrzeżenie na SUCCESS).
- I7 replay idempotency — service ITs, nie oracle.
- TH-401-B out of scope.

## Decisions preserved

Option C · 4 labels · patterns `{SUCCESS,F0,F1,F2,NONE}` · incomplete → AMBIGUOUS+NONE+COMPENSATION_INCOMPLETE · RO JDBC / REPEATABLE READ · no Temporal/LLM.

**Prośba:** kolejny review Ownera przed merge.
