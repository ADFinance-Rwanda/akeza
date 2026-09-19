-- Lookup whether a SCAN_OVERDUE job was created recently (worker enqueue throttle).
CREATE INDEX IF NOT EXISTS idx_jobs_type_created ON jobs (type, created_at);
