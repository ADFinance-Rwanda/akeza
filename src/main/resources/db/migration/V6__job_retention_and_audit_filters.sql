-- Worker due-queue and retention cleanup.
CREATE INDEX IF NOT EXISTS idx_jobs_status_updated ON jobs (status, updated_at);

-- Server-side audit filters (action, actor, time remain tenant-scoped).
CREATE INDEX IF NOT EXISTS idx_audit_org_action_created ON audit_logs (organization_id, action, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_org_user_created ON audit_logs (organization_id, user_id, created_at);
