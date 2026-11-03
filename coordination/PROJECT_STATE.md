# PROJECT_STATE

**Last updated:** 2026-11-03 (implementation handbook + Obsidian sync)  
**Updated by:** Cursor Agent  
**MODE this session:** DOCUMENTATION (handbook + vault; no production code; no live G5)

## Current thesis scope (MVP)

| | |
| --- | --- |
| **Runner** | TH-402 phase-1 + 1.1 + F0–F3 matrix driver + **G5 opt-in lab** on `main` |
| **TH-400 S0–S4-A/B/C** | On `main` (PR #15 / D-408) |
| **TH-400 S4-D** | **Deferred / unauthorized** |
| **TH-402 G5** | **Implementation on `main`** (PR #16 / D-409); **live execution NOT authorized**; ticket **NOT Done** |
| **G5 nature** | Pre-pilot gate (OWN-P4), **not** a merge gate; G5-2 and G5-3 are **independent** live scenarios |
| **Handbook** | `docs/implementation-guide/` + Obsidian `07 - Implementation Handbook/` |
| **Not authorized now** | Live G5 · S4-D · Variant C / TH-303 · Oracle/protocol changes · #35/#37 Done · EXP-* |

## Engineering

| | |
| --- | --- |
| **Branch** | `main` @ `0824c357265c17f2941d9f35b5295c14a51154a9` |
| **PR #16 merge** | `801e343b36058094a91c5f89bcc57c945a860b0a` (ancestor of HEAD) |
| **Last docs commit on main** | `0824c35` — D-409 coordination |
| **PR-head CI** | Run **#70** success on `b6a0423` |
| **Post-merge CI** | Run **#71** success on `801e343` (Owner-reported) |
| **D-AUTH-G5** | Immutable (impl auth; live not authorized at decision time) |
| **Oracle / protocol / R6/R14 / OWN-P1** | Unchanged |
| **EXP-*** | **None** |
| **Taiga sync** | **Proposed only** — see `TAIGA_PROPOSED_UPDATES_2026-11-03.md` (no MCP / no creds) |

## Next three actions

1. Owner: separate decision whether to authorize **live G5** (`THESIS_RUN_G5`, G5-2 and/or G5-3)  
2. Owner: optionally paste proposed Taiga comments; **do not** mark #37 Done  
3. Use `docs/implementation-guide/README.md` / Obsidian Handbook Home as study entry  
