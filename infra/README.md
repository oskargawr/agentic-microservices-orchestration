# Local infrastructure (TH-100)

## Services

| Service | Host endpoint | Notes |
| --- | --- | --- |
| PostgreSQL | `localhost:5433` | User/pass `thesis` / `thesis` |
| DB `business` | schemas `inventory`, `payment`, `shipping` | Domain tables → TH-102..104 |
| DB `temporal` / `temporal_visibility` | used by Temporal auto-setup | |
| Temporal gRPC | `localhost:7233` | Server only — no thesis workflows yet |
| Temporal UI | http://localhost:8088 | |

Port **5433** is intentional: host `:5432` is often already taken.

## Start / stop

```bash
cd infra
docker compose up -d
docker compose ps
docker compose logs -f temporal
docker compose down          # keeps volume
docker compose down -v       # DESTROYS postgres data — avoid unless intentional
```

## Verify schemas

```bash
docker compose exec -T postgres \
  psql -U thesis -d business -c '\dn'
```

Expected: `inventory`, `payment`, `shipping`.
