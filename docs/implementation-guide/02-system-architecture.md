# 02 — System architecture

## Component diagram (as-built Variant A + harness)

```mermaid
flowchart TB
  subgraph Client["Experiment client"]
    TR[TrialRunner / MatrixExperimentDriver]
    G5[G5 opt-in lab IT]
  end

  subgraph TemporalPlane["Temporal"]
    TS[Temporal Server]
    W[orchestration:worker<br/>VariantAWorkers]
    WF[OrderFulfillmentWorkflowImpl]
    ACT[HttpOrderFulfillmentActivities]
  end

  subgraph Services["Spring Boot business services"]
    INV[services:inventory]
    PAY[services:payment]
    SHIP[services:shipping]
  end

  subgraph Data["PostgreSQL"]
    DBi[(inventory schema)]
    DBp[(payment schema)]
    DBs[(shipping schema)]
  end

  subgraph Evidence["Experiment evidence"]
    OR[IndependentOracle + OutcomeClassifier]
    RS[FileResultStore / TrialRecord]
    FC[FaultController family]
  end

  TR -->|gRPC start/describe/terminate| TS
  G5 -->|gRPC + DescribeTaskQueue + history| TS
  W -->|poll task queue| TS
  W --> WF
  WF --> ACT
  ACT -->|HTTP| INV
  ACT -->|HTTP + X-Thesis-Fault| PAY
  ACT -->|HTTP + X-Thesis-Fault| SHIP
  INV --> DBi
  PAY --> DBp
  SHIP --> DBs
  TR --> FC
  FC -->|HTTP verify/arm| PAY
  FC -->|HTTP verify/arm| SHIP
  TR -->|JDBC reset| Data
  OR -->|JDBC snapshot| Data
  TR --> OR
  TR --> RS
  CON[orchestration:console] -.->|manual start| TS
```

### Communication kinds

| Path | Mechanism |
| --- | --- |
| Runner → Temporal | Temporal Java SDK (workflow stub) |
| Worker ↔ Temporal | Task-queue polling |
| Activities → services | HTTP REST |
| Services → DB | JDBC / Spring Data |
| Oracle → DB | JDBC read-only snapshot |
| Fault verify | HTTP probes with fault headers |
| Evidence | Filesystem JSON/CSV via `FileResultStore` |

## Module relationships (Gradle)

```mermaid
flowchart LR
  contracts --> inventory
  contracts --> payment
  contracts --> shipping
  contracts --> varianta[orchestration:variant-a]
  contracts --> oracle[experiment:oracle]
  platform[orchestration:platform] --> varianta
  varianta --> worker[orchestration:worker]
  varianta --> console[orchestration:console]
  oracle --> runner[experiment:runner]
  varianta --> runner
```

## What is not in the diagram

- **Variant C** workflow / LLM activity — not present as a module  
- Toxiproxy / network chaos — not the F1–F3 mechanism (header injection is)  
- Live EXP-* result DB — only file ResultStore for harness artifacts  
