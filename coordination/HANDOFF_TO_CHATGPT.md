# HANDOFF_TO_CHATGPT

**Data:** 2026-11-02  
**Od:** Cursor Agent  
**Tryb:** IMPLEMENTATION (TH-401-A)  
**Status:** implementacja ukończona lokalnie; **PR do review** (nie Done do merge + verify)

---

## 1. Decyzje Ownera (zachowane)

- Option C · 4 etykiety oracle · pattern ∈ {SUCCESS,F0,F1,F2,NONE}
- Incomplete → `AMBIGUOUS` + `pattern=NONE` + reason `COMPENSATION_INCOMPLETE`
- Audyt: `OPTION_C_COMPATIBLE_WITH_FROZEN_PROTOCOL`
- TH-011 / D-011 / D-012 / D-201 / D-208 **bez zmian**
- TH-401-B poza zakresem

---

## 2. Branch / zakres

| | |
| --- | --- |
| Branch | `feature/th-401-independent-oracle` |
| Moduł | `experiment/oracle` |
| Taiga | #36 **In progress** |

### Struktura

```text
experiment/oracle/
  IndependentOracle
  OracleResult / OracleEvaluationRequest / OraclePattern / OracleReasonCode
  classify/OutcomeClassifier          # Option C, pure
  jdbc/OracleSnapshotRepository       # READ ONLY + REPEATABLE READ
  tests: ScenarioClassificationTest (S1–S14+), IndependenceTest (IND-1…6),
         OracleModuleConstraintsTest, OracleJdbcIT
```

---

## 3. Testy

| Suite | Wynik |
| --- | --- |
| `:experiment:oracle:test` | 27 tests, 0 skipped, 0 fail (JDBC IT against :5433) |
| `./gradlew --no-parallel cleanTest test` | **SUCCESS** |
| `./gradlew --no-parallel build` | **SUCCESS** |
| Całość XML | **129** tests, **0** skipped, **0** failures, **0** errors |

Dowody niezależności: `IndependenceTest` + `OracleModuleConstraintsTest` (brak `io.temporal` / spring-ai w sources i build).  
Read-only: `setReadOnly(true)` + `SET TRANSACTION READ ONLY` + `REPEATABLE READ` w jednej transakcji.

---

## 4. Ograniczenia

- Brak HTTP cross-check (TH-401-B)
- Brak runnera / TH-400
- `pattern` nie rozszerzony — reason codes osobno
- JDBC IT skipuje, gdy Postgres niedostępny (`assumeTrue`)

---

## 5. Review

- PR: (link po `gh pr create`)
- Nie startować TH-401-B bez Ownera
- `stash@{0}` nienaruszony
