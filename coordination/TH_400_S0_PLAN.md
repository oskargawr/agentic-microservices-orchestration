# TH-400 — S0 plan pointer

| Field | Value |
| --- | --- |
| **Status** | **Owner-ratified (D-404, 2026-11-02)** — documentation complete |
| **Branch** | `docs/th-400-s0-fault-profile-spec` |
| **Normative spec** | [`docs/architecture/TH_400_FAULT_PROFILE_SPEC.md`](../docs/architecture/TH_400_FAULT_PROFILE_SPEC.md) |
| **Verification** | [`docs/architecture/TH_400_S0_TECHNICAL_VERIFICATION.md`](../docs/architecture/TH_400_S0_TECHNICAL_VERIFICATION.md) |
| **Decision** | **D-404** Owner-ratified; coding **not** authorized |

## Slice reminder (post-S0)

| Slice | Content | Auth |
| --- | --- | --- |
| **S0** | Spec lock (this work) | **Done / ratified** |
| **S1** | F1-P FaultController + payment `CHARGE_FAIL` arming | Needs **separate D-AUTH** |
| **S2** | Shipping F2 `ARRANGE_REJECTED` + idempotent failure replay | Needs D-AUTH |
| **S3** | F3-Recovered + F3-Unresolved (503 header-presence) | Needs D-AUTH |
| **S4** | Cross-service verify/disarm / A×C parity polish | Needs D-AUTH |

## Blockers before coding

1. Explicit **D-AUTH** + `NEXT_TASK=approved` for a named slice (recommended first: **S1**).  
2. Do **not** treat S0 ratification as implementation authorization.
