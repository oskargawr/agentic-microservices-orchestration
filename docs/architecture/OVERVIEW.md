# Architecture overview (placeholder)

**Status:** placeholder — no implementation yet.  
**Scope decision:** Owner-approved MVP **A vs C** (D-009). Supervisor supported the overall topic/idea; detailed methodology remains labeled as research design until further confirmation.

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
        └────────────────────────┘
```

## Non-goals for this document

- No concrete class diagrams yet (TH-101+).
- No claim that the diagram matches running code (none yet).
- End-state success must be verified independently of LLM text (see TH-011).

## Related

- GitHub: https://github.com/oskargawr/agentic-microservices-orchestration
- Protocol: [`docs/protocol/`](../protocol/)
- Coordination: [`coordination/DECISION_LOG.md`](../../coordination/DECISION_LOG.md) (D-009)
- Local Obsidian vault (not in Git): Scope and Non-Goals, Research Questions, Experimental Protocol

