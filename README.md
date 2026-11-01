# Agentic Microservices Orchestration

Experimental evaluation of deterministic and LLM-assisted orchestration for reliable distributed processes in microservice architectures.

**PL:** Analiza niezawodności agentowej orkiestracji procesów rozproszonych w architekturze mikroserwisowej.

| | |
| --- | --- |
| **Repository** | https://github.com/oskargawr/agentic-microservices-orchestration |
| **Local path** | `agentic-orchestration-thesis/` |
| **MVP (approved)** | Variant **A** vs Variant **C** |
| **Status** | Infra bootstrap (TH-100) complete; domain APIs and workflows not yet implemented |

## Research objective

Compare how two orchestration styles behave under controlled faults when driving the same business microservices:

| Variant | Orchestration | Role in MVP |
| --- | --- | --- |
| **A** | Deterministic Temporal workflow with Saga-style compensation | Mandatory baseline |
| **C** | Temporal workflow with **bounded** LLM-assisted decisions | Mandatory contrast |
| **B** | (optional) | Out of MVP unless Owner expands |
| **RQ4** | (optional) | Out of MVP unless Owner expands |

End-state success is verified by an independent oracle against business invariants — **not** by free-form LLM text.

## Current implementation status

| Area | Status |
| --- | --- |
| Gradle multi-module monorepo (JDK 21) | Done |
| Postgres + Temporal local infra (Compose) | Done |
| Schema-per-service (`inventory`, `payment`, `shipping`) | Done |
| Spring Boot service stubs + Actuator health | Done |
| Business invariants & oracle spec (TH-011) | Spec done |
| Experimental protocol v0 (TH-012) | Spec done |
| Domain contracts / state models (TH-101 / 101.1) | Done |
| Inventory service (TH-102) | Done |
| Payment service (TH-103) | Done (incl. deterministic F3) |
| Shipping (TH-104) | Planned |
| Temporal workflows (A / C) | Planned |
| Fault injection + experiment runner | Planned |
| Confirmatory experiment runs | **Not started** |

Do not treat this repository as containing completed experimental results.

## Technology stack

- **Java 21** · Gradle (Kotlin DSL) · Spring Boot (service stubs)
- **PostgreSQL 16** (business DB on host port **5433**)
- **Temporal Server** + Temporal UI (gRPC `:7233`, UI `:8088`)
- Docker Compose for local infrastructure
- GitHub Actions: `./gradlew build` on PRs and `main`

## Repository structure

```text
contracts/                               Shared DTOs, enums, transition guards (TH-101)
services/{inventory,payment,shipping}/   Spring Boot stubs (domain APIs → TH-102..104)
orchestration/platform/                  Future Temporal workers (no workflows yet)
infra/                                   Compose: Postgres + Temporal
docs/contracts/                          OpenAPI + state diagrams
docs/architecture/                       Architecture notes
docs/protocol/                           Invariants, oracle, protocol-v0
docs/DEVELOPMENT_WORKFLOW.md             Branching & Conventional Commits
coordination/                            Owner / ChatGPT / Cursor SoT
experiment/                              Runner / oracle / faults (later)
analysis/                                Analysis notebooks/scripts (later)
AGENTS.md                                Agent role instructions
```

Research literature notes remain in a **local Obsidian vault** (not in this Git repository).

## Prerequisites

- JDK **21** (`JAVA_HOME` must point at a real JDK, not the macOS `/usr/bin/java` stub)
- Docker + Docker Compose
- Git

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
# or e.g. .../ms-21.0.7/Contents/Home
```

Copy env placeholders locally (never commit a filled `.env`):

```bash
cp .env.example .env
```

## Start local infrastructure

```bash
cd infra
docker compose up -d
cd ..
./scripts/verify-infra.sh
```

| Endpoint | URL |
| --- | --- |
| Postgres | `localhost:5433` (user/password `thesis` — local-dev only) |
| Temporal gRPC | `localhost:7233` |
| Temporal UI | http://localhost:8088 |

Details: [`infra/README.md`](infra/README.md).

## Build and run services

```bash
./gradlew build
```

Optional stub smoke (infra must be up):

```bash
./gradlew :services:inventory:bootRun
# curl http://localhost:8081/actuator/health
```

| Stub | Port |
| --- | --- |
| Inventory | `8081` |
| Payment | `8082` |
| Shipping | `8083` |

Basic CI does **not** require Temporal or Postgres — only `./gradlew build`.

## Documentation

- Architecture: [`docs/architecture/OVERVIEW.md`](docs/architecture/OVERVIEW.md)
- Contracts / state models: [`docs/contracts/README.md`](docs/contracts/README.md) · [`docs/contracts/STATE_MODELS.md`](docs/contracts/STATE_MODELS.md)
- Inventory concurrency: [`docs/architecture/INVENTORY_CONCURRENCY.md`](docs/architecture/INVENTORY_CONCURRENCY.md)
- Payment / F3: [`docs/architecture/PAYMENT_CONCURRENCY.md`](docs/architecture/PAYMENT_CONCURRENCY.md)
- Business invariants & oracle: [`docs/protocol/BUSINESS_INVARIANTS_AND_ORACLE.md`](docs/protocol/BUSINESS_INVARIANTS_AND_ORACLE.md)
- Experimental protocol v0: [`docs/protocol/EXPERIMENTAL_PROTOCOL_V0.md`](docs/protocol/EXPERIMENTAL_PROTOCOL_V0.md)
- Git workflow: [`docs/DEVELOPMENT_WORKFLOW.md`](docs/DEVELOPMENT_WORKFLOW.md)
- Coordination: [`coordination/README.md`](coordination/README.md)

## Research limitations and scope

- MVP is **A vs C** only; B and RQ4 are optional.
- Protocol timings (`T_observe`, `T_run_max`) are provisional until a pilot.
- LLM model choice (OWN-2) and confirmatory sample size (OWN-1) remain open Owner decisions.
- No claim that experiments have been conducted or that variant C is superior.

## License / academic use

Thesis engineering artefact. Do not commit copyrighted full-text papers or real secrets.
