# TH-400 — S0 plan pointer

| Field | Value |
| --- | --- |
| **Status** | Documentation complete — **awaiting Owner review** |
| **Branch** | `docs/th-400-s0-fault-profile-spec` |
| **Normative spec** | [`docs/architecture/TH_400_FAULT_PROFILE_SPEC.md`](../docs/architecture/TH_400_FAULT_PROFILE_SPEC.md) |
| **Decision** | Proposed **D-404** (Owner directions accepted; coding **not** authorized) |

## Slice reminder (post-S0, needs D-AUTH)

| Slice | Content |
| --- | --- |
| S0 | Spec lock (this work) |
| S1 | F1-P FaultController + payment arming |
| S2 | Shipping F2 header + idempotent failure replay |
| S3 | F3-Recovered + F3-Unresolved (after OQ-F3U-1) |
| S4 | Cross-service verify/disarm / parity polish |

## Blockers before coding

1. Owner review / ratification of D-404 + OQ-F2-1 / OQ-F3U-1 (and soft OQ-F3U-2).  
2. Explicit **D-AUTH** + `NEXT_TASK=approved` for an implementation slice.
