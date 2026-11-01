# Architecture overview

**Status:** contracts defined (TH-101); service domain logic not implemented (TH-102..104).  
**Scope decision:** Owner-approved MVP **A vs C** (D-009).

## Intended comparison

```text
Client / harness
       │
       ▼
┌──────────────────┐     ┌──────────────────┐
│ Variant A        │     │ Variant C        │
│ Temporal workflow│     │ Temporal workflow│
│ (deterministic)  │     │ + bounded LLM    │
│                  │     │   activity       │
└────────┬─────────┘     └────────┬─────────┘
         │                        │
         └───────────┬────────────┘
                     ▼
        ┌────────────────────────┐
        │ Shared business APIs   │
        │ Inventory · Payment ·  │
        │ Shipping               │
        │ (identical contracts)  │
        └────────────────────────┘
```

## Shared contracts (TH-101)

| Artifact | Path |
| --- | --- |
| Java DTOs + transition guards | module `:contracts` |
| OpenAPI | [`docs/contracts/openapi.yaml`](../contracts/openapi.yaml) |
| State diagrams | [`docs/contracts/STATE_MODELS.md`](../contracts/STATE_MODELS.md) |
| Invariants / oracle | [`docs/protocol/BUSINESS_INVARIANTS_AND_ORACLE.md`](../protocol/BUSINESS_INVARIANTS_AND_ORACLE.md) |

## Non-goals (this document / TH-101)

- No Temporal workflows, LLM, or experiment runner.
- No claim that HTTP endpoints are implemented (stubs only expose Actuator until TH-102..104).
- End-state success must be verified independently of LLM text (TH-011).

## Related

- GitHub: https://github.com/oskargawr/agentic-microservices-orchestration
- Coordination: [`coordination/DECISION_LOG.md`](../../coordination/DECISION_LOG.md) (D-009, D-011, D-012)
