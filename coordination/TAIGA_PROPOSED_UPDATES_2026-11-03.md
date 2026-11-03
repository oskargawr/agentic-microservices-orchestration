# Taiga — proposed updates (2026-11-03)

**STATUS:** **NOT APPLIED** in this session.

## Why not applied

- No Taiga MCP namespace available to the agent.
- `infra/taiga-docker/.credentials.local` absent in this worktree.
- `http://localhost:9000` responds (UI), but unauthenticated `/api/v1/projects?slug=…` returns `[]`.

Do **not** treat this file as evidence that Taiga was updated.

## Proposed factual comments

### #37 — TH-402 / Experiment runner (CRITICAL: leave ≠ Done)

**Comment to add:**

> 2026-11-03 — Source fact: PR #16 merged to `main` (`801e343b36058094a91c5f89bcc57c945a860b0a`). G5 opt-in lab implementation is on `main`. Post-merge CI run #71 SUCCESS on the merge commit (Owner-reported). D-409 recorded. Live G5 (`THESIS_RUN_G5`) has **not** been executed. G5 remains a pre-pilot gate (OWN-P4). No EXP-* entries. **Do not mark Done** — implementation merge ≠ live acceptance / ticket completion.

**Status:** keep **In progress** (or equivalent ≠ Done).  
**Do not:** mark Done, close, or claim live evidence.

### #35 — TH-400 / Fault profiles

**Comment to add:**

> 2026-11-03 — S0–S4-A/B/C implementation on `main` (D-408 / prior PRs). S4-D remains deferred/unauthorized. Matrix driver presence ≠ completed EXP-* campaign. G5 is tracked under #37.

**Status:** comment only unless Owner separately accepts Done criteria (live matrix / campaign). Default: **leave ≠ Done**.

### #30–#34 — Variant C

No change. Still not started / unauthorized.

### #68 — Java cleanup

No change.

## Checklist for Owner (manual)

- [ ] Paste #37 comment  
- [ ] Confirm #37 still not Done  
- [ ] Optional #35 comment  
- [ ] Do not create EXP-* from this documentation work  
