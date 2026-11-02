# Independent Oracle (TH-401-A)

DB-primary, read-only end-state oracle for Variants A and C.

**Spec:** [`docs/protocol/BUSINESS_INVARIANTS_AND_ORACLE.md`](../../docs/protocol/BUSINESS_INVARIANTS_AND_ORACLE.md)  
**Design:** [`docs/architecture/INDEPENDENT_ORACLE_DESIGN.md`](../../docs/architecture/INDEPENDENT_ORACLE_DESIGN.md)

## Usage

```java
DataSource ds = ...; // primary Postgres jdbc:postgresql://localhost:5433/business
IndependentOracle oracle = new IndependentOracle(ds);
OracleResult result = oracle.evaluate(orderId, "SKU-MVP-1");
```

Classification uses only durable Inventory / Payment / Shipping rows (Option C).  
`workflowId` / `trialId` are optional metadata and never affect the label.

## Non-goals (TH-401-A)

- HTTP API cross-check (TH-401-B)
- Experiment runner / fault injection
- Temporal or LLM dependencies
