# Development workflow

GitHub: https://github.com/oskargawr/agentic-microservices-orchestration  
Default branch: **`main`** (stable, verified).

## Branch naming

| Prefix | Use |
| --- | --- |
| `feature/<short-description>` | New capability |
| `fix/<short-description>` | Bug fix |
| `docs/<short-description>` | Documentation only |
| `experiment/<short-description>` | Experimental / exploratory code |
| `chore/<short-description>` | Tooling, CI, deps, infra glue |

Examples: `feature/inventory-reserve`, `docs/protocol-v0-amend`, `chore/ci-gradle`.

## Conventional Commits

```text
feat(scope): description
fix(scope): description
docs(scope): description
test(scope): description
refactor(scope): description
chore(scope): description
```

Examples:

- `feat(inventory): implement reservation`
- `feat(orchestration): add deterministic Temporal workflow`
- `test(payment): cover ambiguous capture outcome`
- `docs(protocol): define F3 fault scenario`
- `chore(infra): update local Temporal configuration`

Do **not** rewrite published history on `main` (`push --force` to `main` is forbidden).

## Everyday loop

```bash
git checkout main
git pull origin main

git checkout -b feature/my-change
# ... edit ...
./gradlew build

git add -A
git commit -m "$(cat <<'EOF'
feat(scope): short summary

EOF
)"

git push -u origin HEAD
gh pr create --fill   # or open PR in the GitHub UI
```

After review and green CI: merge via Pull Request into `main` (prefer squash or merge commit per team preference; do not rebase-force `main`).

```bash
git checkout main
git pull origin main
```

## Pull Requests

Prefer a PR for any meaningful change. Before merge:

1. `./gradlew build` passes (CI).
2. Description links the related `TH-*` ticket when applicable.
3. No secrets in the diff.

## Coordination

Task approval and research decisions live in [`../coordination/`](../coordination/).  
Cursor executes `NEXT_TASK.md` only when status is `approved` by the Owner.
