# HANDOFF_TO_CHATGPT

**Data:** 2026-11-02  
**Od:** Cursor Agent  
**Tryb:** IMPLEMENTATION (TH-401-A — OracleJdbcIT skip/baseline follow-up)  
**PR:** https://github.com/oskargawr/agentic-microservices-orchestration/pull/10  
**Nie zmergowano.**

---

## Findings addressed

### 1. Narrow skip condition

- Extracted `PostgresConnectivity.isUnreachable(SQLException)` — SQLState class `08*` or network causes (`ConnectException`, etc.).
- Auth `28*` / other SQL (e.g. `42P01`) → **not** unreachable → setup **fails**.
- `beforeAll` catches only `SQLException`; Flyway errors still propagate (never skipped).
- Unit tests: `PostgresConnectivityTest` (4).
- Live contrast: `OracleJdbcIT.invalidCredentials_areNotTreatedAsUnreachable` (expects `28*`).
- Probe: primary wrong password → suite fails (not skip).

### 2. `baselineOnMigrate`

**Removed.** Reason:
- Fresh empty schemas: plain `migrate()` applies V1.
- Already migrated: history → idempotent no-op.
- Non-empty without history: Flyway must **fail loudly**; `baselineOnMigrate(true)` would baseline at v1 and **skip** required V1 DDL.

Documented in `OracleJdbcIT` class javadoc.

---

## Files changed

- `experiment/oracle/.../OracleJdbcIT.java`
- `experiment/oracle/.../support/PostgresConnectivity.java` *(new)*
- `experiment/oracle/.../support/PostgresConnectivityTest.java` *(new)*
- `coordination/HANDOFF_TO_CHATGPT.md`

---

## Verification

| Scenario | Result |
| --- | --- |
| Fresh empty schemas | JdbcIT **RAN** (3 tests, 0 skip) |
| Already migrated | JdbcIT **RAN** |
| Unreachable (`127.0.0.1:1`) | JdbcIT **SKIP** (3); connectivity unit **RAN** |
| Invalid primary credentials | Suite **FAIL** (not skip) |
| `./gradlew --no-parallel :experiment:oracle:cleanTest :experiment:oracle:test` | SUCCESS |
| `./gradlew --no-parallel cleanTest test` | SUCCESS · aggregate **135** / 0 skip / 0 fail / 0 err |
| `./gradlew --no-parallel build` | SUCCESS |

Classification / Option C / protocol unchanged. No Temporal/LLM. TH-401-B not started.

**Commit SHA:** *(after push)*  
Await Owner review; **do not merge** without Owner.
