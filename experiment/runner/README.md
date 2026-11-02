# Experiment Runner (TH-402 phase-1)

Bounded **F0 × Variant A** harness skeleton.

## Module

`:experiment:runner` — reusable library + CLI (`RunnerMain`). **Not** a Spring Boot HTTP service.

## Phase-1 scope

- Trial lifecycle: `COMPLETED` | `EXCLUDED` | `ABORTED_EXPERIMENT`
- Serial execution + exclusive env file lock
- Fail-closed JDBC allowlist before destructive reset
- File-first JSON + CSV results (`experiment/results/{phase}/{experimentId}/`)
- Variant A Temporal adapter (terminate on `T_run_max`, not cancel)
- R14: quiesce → full `T_observe` → Independent Oracle once
- F0 FaultController (no-op)

## Not in phase-1

TH-400 / F1–F3 injectors, Variant C, Postgres results DB, confirmatory runs.

## CLI (example)

```bash
./gradlew :experiment:runner:run --args='\
  --jdbc-url=jdbc:postgresql://localhost:5433/business \
  --allowlist=jdbc:postgresql://localhost:5433/business \
  --results-dir=experiment/results'
```

Requires Temporal + Variant A worker + business services running against the allowlisted DB.

## Tests

```bash
./gradlew :experiment:runner:test
```
