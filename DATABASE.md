# Database

Flyway migrations: `src/main/resources/db/migration/V1__init.sql`, `V2__task_filter_index.sql`, `V3__task_created_by.sql`, `V4__assessment_domain.sql`, `V5__phase2_hardening.sql`

Hibernate `ddl-auto` is `none`. Schema is owned by Flyway.

Tables: `users`, `organizations`, `memberships`, `projects`, `tasks`, `audit_logs`, `idempotency_keys`, `jobs`.

Important constraints (verified live on PostgreSQL 17.11 after `docker compose up --build`, 2026-09-17):

- Unique organization slug, user email, Keycloak subject
- Unique membership `(organization_id, user_id)` — `uk_membership_org_user`
- Unique idempotency `(organization_id, user_id, key_value)` — `uk_idempotency_org_user_key`
- `tasks.due_date` (DATE, nullable) and `tasks.completed_at` (set when status becomes DONE; used for completion trends)
- `tasks` status values: `TODO`, `IN_PROGRESS`, `DONE` (legacy `BLOCKED` rows migrated to `IN_PROGRESS` in V4)
- Priorities: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- `projects.status`: `ACTIVE` (default) or `ARCHIVED`
- Membership roles: `ORG_ADMIN`, `PROJECT_MANAGER`, `MEMBER` (V4 renamed OWNER/ADMIN/VIEWER)
- `users.super_admin` boolean
- `idempotency_keys.completed_at` plus created/completed indexes (V5 retention)
- `tasks.overdue_notified_at` (worker overdue scan)
- `jobs.dead_lettered_at` (FAILED is the dead-letter state)
- Indexes: `idx_tasks_org_due_date`, `idx_tasks_org_assignee` (overdue/filter queries)
- `tasks.created_by_user_id` FK + `idx_tasks_created_by` (V3)
- FKs: `tasks_organization_id_fkey`, `tasks_project_id_fkey`, `tasks_assignee_user_id_fkey`, `tasks_created_by_user_id_fkey`
- Indexes: `idx_tasks_org_project`, `idx_tasks_org_status`, `idx_tasks_org_priority`, `idx_tasks_org_project_status` (V2)

Analytics dashboard (`GET /api/analytics/dashboard`) is tenant-scoped:

- `pendingTasks` = TODO + IN_PROGRESS
- `overdueTasks` = `due_date < today UTC` AND status != DONE
- Trends: last 14 UTC days of creates (`created_at`) and completions (`completed_at`)

Timezone for overdue and trend buckets is **UTC** (`Clock.systemUTC()`; Docker Postgres `CURRENT_DATE` is not used for overdue — Java UTC date is bound in JPQL).

## EXPLAIN ANALYZE (live, 2026-09-17)

Target query (task list filter used by `GET /api/projects/{id}/tasks?status=TODO`):

```sql
EXPLAIN ANALYZE
SELECT * FROM tasks
WHERE organization_id = 1 AND project_id = 1 AND status = 'TODO';
```

Dataset at capture time: one matching row (demo seed). Wall-clock times on a one-row table are not a useful speedup metric; the **plan shape** is.

### 1. Query before V2 (index dropped inside a rolled-back transaction)

```
Index Scan using idx_tasks_org_priority on tasks
  (cost=0.14..8.17 rows=1 width=1114) (actual time=1.740..1.805 rows=1 loops=1)
  Index Cond: (organization_id = 1)
  Filter: ((project_id = 1) AND ((status)::text = 'TODO'::text))
Planning Time: 47.754 ms
Execution Time: 2.263 ms
```

Problem: `idx_tasks_org_priority (organization_id, priority)` only constrains `organization_id`. `project_id` and `status` are applied as a **Filter** after the index scan.

### 2. Optimization

Flyway `V2__task_filter_index.sql`:

```sql
CREATE INDEX idx_tasks_org_project_status ON tasks (organization_id, project_id, status);
```

### 3. Query after V2 (index present)

```
Index Scan using idx_tasks_org_project_status on tasks
  (cost=0.14..8.16 rows=1 width=1114) (actual time=1.810..1.844 rows=1 loops=1)
  Index Cond: ((organization_id = 1) AND (project_id = 1) AND ((status)::text = 'TODO'::text))
Planning Time: 67.835 ms
Execution Time: 7.879 ms
```

The same table also used this index for org+project without status (left-prefix), execution **0.633 ms**.

### 4. Observed improvement

The planner moved `project_id` and `status` from **Filter** into **Index Cond** on `idx_tasks_org_project_status`. That is the intended win for filtered pagination as the table grows. On this one-row demo database, execution milliseconds did not improve (first capture after a cold plan was even slightly slower). Do not treat the millisecond values as a production benchmark.

## Recapture (2026-09-18, ~63 rows)

After additional demo tasks existed, the same query used a sequential scan:

```
Seq Scan on tasks  (cost=0.00..2.08 rows=19 width=623) (actual time=0.030..0.046 rows=32 loops=1)
  Filter: ((organization_id = 1) AND (project_id = 1) AND ((status)::text = 'TODO'::text))
  Rows Removed by Filter: 31
Planning Time: 4.463 ms
Execution Time: 0.141 ms
```

`idx_tasks_org_project_status` is still present. On this small table the planner prefers a seq scan (cost ~2) over the index. That is expected; the V2 index is for filtered pagination as the table grows, not a micro-benchmark on dozens of rows.

## Backup / restore

```powershell
.\scripts\backup.ps1
.\scripts\restore.ps1 -BackupFile .\backups\tenant_management-YYYYMMDD-HHMMSS.sql
```

```bash
./scripts/backup.sh
./scripts/restore.sh backups/tenant_management-YYYYMMDD-HHMMSS.sql
```

See `DISASTER_RECOVERY.md`.
