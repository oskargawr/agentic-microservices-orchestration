# 13 — Supervisor preparation
**Count:** 30 questions.
Answer aloud in Polish; identifiers in English.

## 1. Architecture
### A1. Why Temporal instead of a simple REST orchestrator?
- **Aloud:** Potrzebuję trwałej historii, retry i odzyskiwania po restarcie procesu orkiestratora — Temporal to daje out of the box.
- **Deeper:** Variant A koduje sagę w workflow deterministycznym; status Temporal ≠ wynik biznesowy (Oracle).
- **Classes / methods:** `OrderFulfillmentWorkflowImpl`, `VariantAWorkers`
- **Diagram / test:** 02 architecture
- **Caveat:** Variant C niezaimplementowane — nie porównuję live A vs C.
### A2. What are the main runtime components?
- **Aloud:** Trzy serwisy Spring, worker Temporal, serwer Temporal, runner eksperymentu, Oracle JDBC, ResultStore.
- **Deeper:** Patrz diagram w 02-system-architecture.
- **Classes / methods:** modules w settings.gradle.kts
- **Diagram / test:** 02
- **Caveat:** Console jest opcjonalnym klientem operatorskim.
### A3. Is Variant C implemented?
- **Aloud:** Nie. MVP zakłada A vs C, ale w repo jest tylko Variant A.
- **Deeper:** Brak modułu variant-c; DECISION_LOG blokuje start bez auth.
- **Classes / methods:** —
- **Diagram / test:** 01
- **Caveat:** Nie mówić o wynikach A vs C.
### A4. What does the task queue name mean?
- **Aloud:** `variant-a-order-fulfillment` — kolejka workerów Variant A.
- **Deeper:** Stała `OrderFulfillmentWorkflow.TASK_QUEUE`; G5 DescribeTaskQueue używa tej nazwy.
- **Classes / methods:** `OrderFulfillmentWorkflow`
- **Diagram / test:** 10 G5
- **Caveat:** Inna kolejka = inne pollery.

## 2. Microservices and distributed transactions
### M1. What is the saga here?
- **Aloud:** Sekwencja reserve→charge→arrange→consume z kompensacją refund/release.
- **Deeper:** Kompensacja w catch workflow; typy BUSINESS vs COMPENSATION_INCOMPLETE.
- **Classes / methods:** `OrderFulfillmentWorkflowImpl`
- **Diagram / test:** 04 F0/F1/F2
- **Caveat:** Kompensacja ≠ Temporal cancel.
### M2. How do distributed transactions work without 2PC?
- **Aloud:** Przez sagę z kompensacjami i idempotencją, nie przez globalny commit.
- **Deeper:** Stany domenowe w osobnych schematach Postgres.
- **Classes / methods:** services + contracts transitions
- **Diagram / test:** 05
- **Caveat:** Oracle sprawdza end-state, nie 2PC.
### M3. What happens if payment times out after processing a charge?
- **Aloud:** To jest motyw F3: odpowiedź może być utracona mimo CAPTURED w DB; resolve ma odczytać prawdę.
- **Deeper:** F3-Recovered vs Unresolved zależy od opacity resolve.
- **Classes / methods:** Payment F3 + workflow resolve
- **Diagram / test:** 05 F3
- **Caveat:** Nie mylić timeout activity z business FAILED.
### M4. How do you distinguish unknown outcome from failed payment?
- **Aloud:** FAILED → ścieżka failure/kompensacja; UNKNOWN/PENDING → AMBIGUOUS jeśli nieodtworzone.
- **Deeper:** D-012: unresolved nigdy nie jest czystym F0–F2 failure.
- **Classes / methods:** `OutcomeClassifier`
- **Diagram / test:** 08
- **Caveat:** OraclePattern ≠ profil F1-P.

## 3. Temporal
### T1. What is TestWorkflowEnvironment?
- **Aloud:** Wbudowany testowy Temporal in-process do CompIT.
- **Deeper:** Szybkie testy sagi; nie dowodzi G5-3 exclusivity.
- **Classes / methods:** HappyPathIT / CompensationIT
- **Diagram / test:** 09
- **Caveat:** CI CompIT ≠ live G5.
### T2. TERMINATED vs COMPLETED vs harness COMPLETED?
- **Aloud:** COMPLETED/TERMINATED to Temporal; harness COMPLETED znaczy runner domknął observe+Oracle+persist.
- **Deeper:** G5-3 często: Temporal TERMINATED + harness COMPLETED.
- **Classes / methods:** `TrialRunner`, TrialRecord fields
- **Diagram / test:** 10
- **Caveat:** COMPLETED harness ≠ G5-3 VALID.
### T3. Why terminate not cancel?
- **Aloud:** Protokół R6 wymaga terminate z powodem T_run_max.
- **Deeper:** Cancel to inne API/semantyka; testy liczą terminate.
- **Classes / methods:** `TrialRunner`, `CountingVariantAdapter`
- **Diagram / test:** 04 G5-3
- **Caveat:** Cleanup reason `g5-cleanup` ≠ measured R6.
### T4. What do activity retries do to side effects?
- **Aloud:** Retry mogą ponawiać HTTP; stąd idempotency keys i DoNotRetry na BUSINESS.
- **Deeper:** lostIdRecovery ma maximumAttempts=1.
- **Classes / methods:** `ActivityPolicies`, idempotency tests
- **Diagram / test:** 07
- **Caveat:** Retry ≠ kompensacja.

## 4. Failure handling and compensation
### F1. Where is compensation implemented?
- **Aloud:** W `OrderFulfillmentWorkflowImpl` po wyjątku forward path.
- **Deeper:** Refund tylko CAPTURED; release tylko HELD.
- **Classes / methods:** CompensationIT
- **Diagram / test:** 04 F1-P/F2
- **Caveat:** Incomplete compensation → osobny typ błędu.
### F2. What does F2 prove about money and shipping?
- **Aloud:** Po CAPTURED odrzucenie arrange musi zwrócić pieniądze i zwolnić hold.
- **Deeper:** Expected Oracle CONFIRMED_FAILURE.
- **Classes / methods:** F2FaultController, TrialRunnerF2LifecycleTest
- **Diagram / test:** 05
- **Caveat:** To nie jest profil G5.

## 5. Fault injection
### I1. How is fault injection done?
- **Aloud:** Nagłówkiem HTTP X-Thesis-Fault, weryfikowanym fail-closed przed startem trialu.
- **Deeper:** Nie Toxiproxy w kanonicznych F1–F3.
- **Classes / methods:** FaultController.verify + HTTP verifiers
- **Diagram / test:** 05
- **Caveat:** Arm bez verify success → EXCLUDED.
### I2. Why verify after arm?
- **Aloud:** Żeby nie zbierać wyników gdy serwis faktycznie nie honoruje faultu.
- **Deeper:** Payment/Shipping verifiers sprawdzają zachowanie.
- **Classes / methods:** `HttpPaymentFaultInjectionVerifier`
- **Diagram / test:** 08
- **Caveat:** Verify ≠ Oracle.

## 6. Oracle and classification
### O1. Why is the Oracle independent?
- **Aloud:** Bo sukces end-state musi wynikać ze stanu biznesowego w DB, nie z tekstu LLM ani statusu Temporal.
- **Deeper:** TH-011 / D-011 Option C.
- **Classes / methods:** `IndependentOracle`, IndependenceTest
- **Diagram / test:** 08
- **Caveat:** Cztery labele w tym INVALID.
### O2. What are the four Oracle labels?
- **Aloud:** CONFIRMED_SUCCESS, CONFIRMED_FAILURE, AMBIGUOUS, INVALID.
- **Deeper:** INVALID = naruszenie invariantów snapshotu.
- **Classes / methods:** `BusinessOutcome`
- **Diagram / test:** 12
- **Caveat:** Nie ma piątego labelu SUCCESS bez CONFIRMED_.
### O3. What does AMBIGUOUS mean for F3-Unresolved?
- **Aloud:** Płatność zostaje UNKNOWN/PENDING przy opaque resolve — nie da się potwierdzić failure/success.
- **Deeper:** ExpectedCellOutcome wymaga ekstra gate.
- **Classes / methods:** OutcomeClassifier + F3Unresolved gate
- **Diagram / test:** 05
- **Caveat:** AMBIGUOUS ≠ INVALID.

## 7. Experimental methodology
### E1. What is the experimental matrix?
- **Aloud:** Wiersze profili F0…F3-* z oczekiwaną klasyfikacją Oracle.
- **Deeper:** MatrixExperimentDriver + ExpectedCellOutcome.
- **Classes / methods:** accept package
- **Diagram / test:** 06
- **Caveat:** Brak EXP-* = brak zmierzonych kampanii.
### E2. Difference between S3 and F3?
- **Aloud:** S3 to faza implementacji F3 w harnessie; F3-* to profile runtime.
- **Deeper:** S* ≠ fault ids.
- **Classes / methods:** CanonicalFaultProfiles vs PROJECT_STATE
- **Diagram / test:** 06
- **Caveat:** Częsty błąd terminologiczny na obronie.
### E3. What does CI success prove?
- **Aloud:** Że zautomatyzowane testy (w tym CompIT) przeszły — nie live G5 ani EXP-*.
- **Deeper:** G5 opt-in wyłączony domyślnie.
- **Classes / methods:** G5OptInCiSafetyTest
- **Diagram / test:** 09/10
- **Caveat:** CI #71 ≠ live-tested.

## 8. Test strategy
### S1. Layers of tests?
- **Aloud:** Unit classifier, service IT, CompIT Temporal, gated real E2E, opt-in G5 live.
- **Deeper:** Patrz 09-test-catalogue.
- **Classes / methods:** wiele klas testowych
- **Diagram / test:** 09
- **Caveat:** Nie każdy poziom jest w każdym PR.
### S2. What does CompensationIT not prove?
- **Aloud:** Nie dowodzi ENV lock, FileResultStore immutability ani G5 history gate.
- **Deeper:** To CompIT sagi, nie pełny harness lab.
- **Classes / methods:** OrderFulfillmentCompensationIT
- **Diagram / test:** 09
- **Caveat:** Potrzeba TrialRunner* + G5 testów.

## 9. G5 and evidence integrity
### G1. What does G5 actually validate?
- **Aloud:** Gotowość runnera na prawdziwym Temporal: start, natural complete, terminate bez pollerów, Oracle once.
- **Deeper:** Pre-pilot OWN-P4; nie wynik eksperymentu naukowego.
- **Classes / methods:** RunnerLiveTemporalG5IT, discovery
- **Diagram / test:** 10
- **Caveat:** Implementacja na main ≠ wykonanie live.
### G2. Why can Temporal be TERMINATED while harness is COMPLETED?
- **Aloud:** Bo R6 kończy workflow terminate, a harness i tak robi observe+Oracle i zapisuje COMPLETED.
- **Deeper:** To oczekiwany kształt G5-3.
- **Classes / methods:** TrialRunner
- **Diagram / test:** 10
- **Caveat:** VALID acceptance osobno.
### G3. Why history pagination matters?
- **Aloud:** Bez opróżnienia nextPageToken gate może nie zobaczyć WorkflowExecutionTerminated.
- **Deeper:** G5HistoryFetcher must drain pages.
- **Classes / methods:** `G5HistoryFetcher`
- **Diagram / test:** 10
- **Caveat:** Fail-closed jeśli historia niekompletna.
### G4. What does current implementation NOT prove?
- **Aloud:** Nie dowodzi live G5, statystyk A vs C, ani Variant C.
- **Deeper:** Brak EXP-*; C niezaimplementowane; S4-D unauthorized.
- **Classes / methods:** PROJECT_STATE
- **Diagram / test:** 01/06
- **Caveat:** Nie przeceniać merge PR #16.

## 10. Limitations and future work
### L1. Main threats to validity?
- **Aloud:** Lab header faults ≠ produkcyjny chaos sieci; CompIT ≠ prod Temporal; brak C.
- **Deeper:** Threats note w research design vault; tu engineering limits.
- **Classes / methods:** —
- **Diagram / test:** 01
- **Caveat:** Nie twierdzić confirmatory bez EXP-*.
### L2. What remains unauthorized?
- **Aloud:** Live G5, S4-D, Variant C/TH-303, #37 Done, inventing EXP-*.
- **Deeper:** D-409 / PROJECT_STATE.
- **Classes / methods:** DECISION_LOG
- **Diagram / test:** 06
- **Caveat:** Propozycja ≠ zgoda Ownera.
