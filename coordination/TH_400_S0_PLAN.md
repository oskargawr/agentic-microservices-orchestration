# TH-400 — S0 plan pointer

| Field | Value |
| --- | --- |
| **Status** | OQ follow-up incorporated — **awaiting final Owner ratification** |
| **Branch** | `docs/th-400-s0-fault-profile-spec` |
| **Normative spec** | [`docs/architecture/TH_400_FAULT_PROFILE_SPEC.md`](../docs/architecture/TH_400_FAULT_PROFILE_SPEC.md) |
| **Verification** | [`docs/architecture/TH_400_S0_TECHNICAL_VERIFICATION.md`](../docs/architecture/TH_400_S0_TECHNICAL_VERIFICATION.md) |
| **Decision** | **D-404** (directions + OQ-F2-1/F3U-1/F3U-2; coding **not** authorized) |

## Slice reminder (post-S0, needs D-AUTH)

| Slice | Content |
| --- | --- |
| S0 | Spec lock (this work) |
| S1 | F1-P FaultController + payment arming |
| S2 | Shipping F2 header + idempotent failure replay |
| S3 | F3-Recovered + F3-Unresolved (after OQ-F3U-1) |
| S4 | Cross-service verify/disarm / parity polish |

## Blockers before coding

1. Final Owner ratification of revised S0 / D-404.  
2. Explicit **D-AUTH** + `NEXT_TASK=approved` for an implementation slice (OQs F2-1 / F3U-1 / F3U-2 are closed in docs).
