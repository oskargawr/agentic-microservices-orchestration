# Coordination — three-party thesis workflow

**Canonical location:** this directory inside the GitHub repository  
https://github.com/oskargawr/agentic-microservices-orchestration

Lightweight Markdown handoff between:

| Role | Responsibility |
| --- | --- |
| **You (Project Owner)** | Final decisions on scope, priorities, architecture, research conclusions. Approves tasks. |
| **ChatGPT (Advisor)** | Literature, methodology, architecture review, experiment design, critique, planning. Reviews `HANDOFF_TO_CHATGPT.md`. |
| **Cursor Agent (Executor)** | Code/tests/experiments, vault updates, evidence-based handoffs. Does **not** invent Owner approval. |

## Files

| File | Purpose |
| --- | --- |
| [PROJECT_STATE.md](PROJECT_STATE.md) | Concise session-start state |
| [NEXT_TASK.md](NEXT_TASK.md) | Current task (proposed → approved by Owner → …) |
| [HANDOFF_TO_CHATGPT.md](HANDOFF_TO_CHATGPT.md) | External review report |
| [DECISION_LOG.md](DECISION_LOG.md) | Decisions with proposed/approved status |
| [EXPERIMENT_LOG.md](EXPERIMENT_LOG.md) | Index of runs (not raw data) |
| [TH-205_CLOSURE.md](TH-205_CLOSURE.md) | TH-205 Done/Closed evidence |
| [TH-206_PROPOSAL.md](TH-206_PROPOSAL.md) | Proposed CI E2E fail-hard ticket |
| [TH-201_IMPLEMENTATION_PLAN.md](TH-201_IMPLEMENTATION_PLAN.md) | TH-201 plan + Pending decisions |

## Protocol (summary)

**Start:** read PROJECT_STATE, NEXT_TASK, relevant decisions; inspect real code/notes; confirm acceptance criteria; ask Owner if a decision is required.

**End:** update PROJECT_STATE; update NEXT_TASK only if state/priorities changed; append decisions/experiments when applicable; sync canonical Obsidian notes; refresh HANDOFF_TO_CHATGPT.

Do not auto-approve advisor suggestions. Do not mark decisions approved without Owner instruction.

## Git conventions (engineering)

See [`docs/DEVELOPMENT_WORKFLOW.md`](../docs/DEVELOPMENT_WORKFLOW.md).

- Branch: `main` + `feature|fix|docs|experiment|chore/<short-description>`
- Commits: Conventional Commits (`feat(scope): …`, `docs(scope): …`, …)
- Prefer Pull Requests into `main`; CI must pass `./gradlew build`

## Related systems (do not duplicate)

- Obsidian vault (local, **not** in Git): `politechnika/Praca magisterska - Orkiestracja agentowa/` — research SoT for literature/RQ
- Ticket board: local **Taiga** (optional) — vault `Backlog.md` is a migration snapshot
- Agent rules in-repo: [`../AGENTS.md`](../AGENTS.md), [`.cursor/rules/thesis-agent.mdc`](../.cursor/rules/thesis-agent.mdc)
