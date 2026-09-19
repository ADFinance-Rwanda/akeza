-- Phase 2: idempotency retention, overdue worker marker, job DLQ timestamp.

ALTER TABLE idempotency_keys ADD COLUMN completed_at TIMESTAMP;
CREATE INDEX idx_idempotency_created_at ON idempotency_keys (created_at);
CREATE INDEX idx_idempotency_completed_at ON idempotency_keys (completed_at);

ALTER TABLE tasks ADD COLUMN overdue_notified_at TIMESTAMP;
CREATE INDEX idx_tasks_overdue_scan ON tasks (due_date, status);

ALTER TABLE jobs ADD COLUMN dead_lettered_at TIMESTAMP;
CREATE INDEX idx_jobs_org_status ON jobs (organization_id, status);
