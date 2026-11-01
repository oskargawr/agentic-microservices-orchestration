# Shared API contracts (TH-101 / TH-101.1)

| File | Role | Server |
| --- | --- | --- |
| [`components.yaml`](components.yaml) | Shared schemas, parameters, error codes | — |
| [`openapi-inventory.yaml`](openapi-inventory.yaml) | Inventory paths | `http://localhost:8081` |
| [`openapi-payment.yaml`](openapi-payment.yaml) | Payment paths | `http://localhost:8082` |
| [`openapi-shipping.yaml`](openapi-shipping.yaml) | Shipping paths | `http://localhost:8083` |
| [`STATE_MODELS.md`](STATE_MODELS.md) | State machines, idempotency, resolve, consume | — |

Java DTOs / guards: Gradle module `:contracts`.

**API prefix (Owner C-101-3):** `/api/v1`.  
**Money (Owner C-101-2):** `amountMinor` + `currency`.  
**Consume (Owner C-101-1):** orchestration after confirmed Shipping `arrange`.
