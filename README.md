# Agentic Orchestration Thesis

**PL:** Analiza niezawodności agentowej orkiestracji procesów rozproszonych w architekturze mikroserwisowej  
**EN:** Analysis of the Reliability of Agent-Based Orchestration of Distributed Processes in a Microservices Architecture

## MVP scope (Owner-approved — D-009)

| | |
| --- | --- |
| **Mandatory** | **A** — deterministic Temporal + saga compensation · **C** — Temporal + bounded LLM decisions |
| **Shared system** | Three local Spring Boot services: inventory, payment (simulation), shipping |
| **Optional (not MVP)** | Variant B (no Temporal), MCP-as-transport, RQ4 adaptivity |

Research coordination (Owner / ChatGPT / Cursor): sibling folder `../coordination/`.  
Knowledge base: Obsidian vault under `../politechnika/Praca magisterska - Orkiestracja agentowa/`.  
PM board: local Taiga — see `../infra/taiga-docker/README-LOCAL.md`.

## Repository layout

```
apps/                 # future runnable entrypoints / compose overlays
services/             # inventory, payment, shipping (not implemented yet)
orchestration/
  variant-a/          # Temporal workflows — deterministic
  variant-c/          # Temporal + bounded LLM activities
experiment/
  runner/             # experiment runner
  faults/             # fault profiles
  oracle/             # independent business-state checks
analysis/             # scripts / notebooks for RQ metrics
docs/
  architecture/       # overview placeholders
  adr/                # architecture decision records
  protocol/           # experiment protocol drafts (links to vault)
config/               # shared non-secret config examples
scripts/              # local helper scripts
```

## Status

**Bootstrap only.** No business services, Temporal workflows, LLM integration, or experiments are implemented in this commit.

Next research/engineering task: **TH-011** — freeze business invariants and independent end-state oracle (`../coordination/NEXT_TASK.md`).

## Local development (planned)

Stack (provisional until locked in ADRs): Java, Spring Boot, Temporal, local LLM (e.g. Ollama). Exact versions and module boundaries will be added with TH-100+.

## License / academic use

Personal master's thesis project. Do not commit secrets, API keys, or large model weights.
