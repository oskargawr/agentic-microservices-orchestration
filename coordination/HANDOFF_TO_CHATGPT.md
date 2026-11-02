# HANDOFF_TO_CHATGPT

**Data:** 2026-11-02  
**Od:** Cursor Agent  
**Tryb:** IMPLEMENTATION (TH-401-A — OracleJdbcIT CI fix)  
**PR:** https://github.com/oskargawr/agentic-microservices-orchestration/pull/10  
**Nie zmergowano.** TH-401-B nie rozpoczęte.

---

## 1. Root cause (evidenced)

| | |
| --- | --- |
| CI run | [41432385608](https://github.com/oskargawr/agentic-microservices-orchestration/actions/runs/41432385608) |
| Failures | `OracleJdbcIT.jdbc_successSnapshot_roundTrip` @ L84 (INSERT); `jdbc_f0_emptyOrder_readOnlyEvaluate` @ L71 (`evaluate`) |
| Infra init | `infra/postgres/init/01-init-databases.sh` creates DB `business` + **empty** schemas `inventory`/`payment`/`shipping` — **no tables** |
| Tables | Created only by Flyway when **service** apps/tests start (`services/*/db/migration/V1__*.sql`) |
| Gradle order | `:experiment:oracle:test` runs **before** service modules (CI log: oracle FAILED after 12 tasks; services never migrated) |
| Local pass | Long-lived Postgres already had tables from prior service ITs |

**Expected PostgreSQL error:** `ERROR: relation "inventory.reservations" does not exist` (SQLState **`42P01`**) — same class for missing `inventory.stock` / etc. on evaluate. CI console truncated the message to `PSQLException at OracleJdbcIT.java:…`; local wipe of schemas reproduced the missing-relation failure mode before the fix.

**Hypothesis confirmed:** OracleJdbcIT depended on foreign module tests / persistent local schema state.

---

## 2. Fix

| Change | Rationale |
| --- | --- |
| `experiment/oracle/build.gradle.kts` | Test Flyway deps; `processTestResources` copies **canonical** service SQL into `classpath:db/migration/{inventory,payment,shipping}` (no DDL fork) |
| `OracleJdbcIT.beforeAll` | When DB reachable: Flyway.migrate each schema; then tests run. Skip **only** if Postgres unreachable |

Oracle evaluate remains read-only + REPEATABLE READ. Classification unchanged.

---

## 3. Fresh-DB verification

1. `DROP SCHEMA inventory/payment/shipping CASCADE` + recreate empty schemas on Compose Postgres.  
2. `./gradlew --no-parallel :experiment:oracle:cleanTest :experiment:oracle:test` → **SUCCESS**; both Jdbc tests **executed** (not skipped); tables appeared via Flyway.  
3. `./gradlew --no-parallel cleanTest test` → SUCCESS  
4. `./gradlew --no-parallel build` → SUCCESS  

Aggregate XML: **0 skipped / 0 failures / 0 errors** (full suite). Oracle module: 28 tests, Jdbc both RAN.

---

## 4. Commit / review

- Push follow-up commit on `feature/th-401-independent-oracle` (SHA after push).  
- Await Owner re-review / CI green. **Do not merge** without Owner.
