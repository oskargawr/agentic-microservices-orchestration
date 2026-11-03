# HANDOFF_TO_CHATGPT

**Data:** 2026-11-03  
**Od:** Cursor Agent  
**Tryb:** IMPLEMENTATION — TH-400 S3 clean history + push + PR (merge **NOT** authorized)  
**Branch:** `feature/th-400-s3-f3`

---

## HEAD / commits (clean S3-only, Scenario B)

| | |
| --- | --- |
| Tip | `6fafd1109c149b5e7a3b3e9bd7b40df20077138a` (+ docs tip after this handoff if amended) |
| Remediacja | `57d5439b97edd35a8af215e277f51f7ab2b6ec6e` |
| S3 impl | `244075e033a0e9a1e7126d361cc8d8f0ab6daf66` |
| Docs after impl | `a13e916a926a68e4e45cc17b0cd0df677bdad604` |
| `c16e1f0` | **excluded** (rebase `--onto origin/main c16e1f0`) |
| Merge-base / `origin/main` | `f3ac351289f1cc7836904d98b3146872f2e62c4f` |

```text
origin/main
  └─ 244075e feat(th-400-s3): implement F3-Recovered and F3-Unresolved …
  └─ a13e916 docs(th-400-s3): record S3 implementation HEAD in handoff
  └─ 57d5439 fix(th-400-s3): narrow opacity ActivityFailure catch and tighten gate
  └─ 6fafd11 docs(th-400-s3): record remediation HEAD in handoff
  └─ (this docs tip: clean-history / push-PR handoff)
```

## Scope shipped

- Dual profiles **F3-Recovered** + **F3-Unresolved**
- Payment `RESOLVE_TRANSPORT_OPAQUE` → HTTP **503**, header-presence, no DB mutation
- Variant A: fault hint on resolve **and** `getPayment`; leave-dangling CAPTURED+HELD for Unresolved
- Harness `F3UnresolvedAcceptanceGate` (Oracle classifier **unchanged**)
- HIGH remediacja: `ActivityFailure` → opacity only when hint + cause-chain `HTTP_503`

## Tests (post-rebase)

```text
java -jar gradle/wrapper/gradle-wrapper.jar \
  :orchestration:variant-a:test :experiment:runner:test :services:payment:test --rerun-tasks
→ BUILD SUCCESSFUL · exit 0
variant-a 37 passed / 0 failed / 0 skipped
runner 72 passed / 0 failed / 0 skipped
payment 14 passed / 0 failed / 0 skipped
```

CompIT: real Postgres + Boot jars + `TestWorkflowEnvironment` — **not** live Temporal (S3-E not performed).  
Retry: behavioral verification only (not quantitative attempt-budget counts).

## Limitations / LOW notes for reviewers

- No live Temporal S3-E
- Nested `HTTP_503` in cause chain is theoretical residual
- Gate unit gaps: payment≠CAPTURED / HELD / invariants (logic present)
- F3 lifecycle disarm path not explicitly asserted (mocked runner)
- `stash@{0}` preserved locally (`b925bb3692c0d87ea713f86288147db3bb1b5cf9`)

## Authorization boundary

| | |
| --- | --- |
| Clean rebase / push / open PR | **authorized** (Owner) |
| **Merge / auto-merge** | **NOT authorized** |
| Taiga #35 / #37 Done | **NOT** |

## Ask

Review PR; merge only after separate Owner auth. Do not mark #35 Done.
