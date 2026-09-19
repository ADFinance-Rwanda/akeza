-- Phase 1 assessment alignment: due dates, project status, role rename, BLOCKED removal.

ALTER TABLE tasks ADD COLUMN due_date DATE;
ALTER TABLE tasks ADD COLUMN completed_at TIMESTAMP;

CREATE INDEX idx_tasks_org_due_date ON tasks (organization_id, due_date);
CREATE INDEX idx_tasks_org_assignee ON tasks (organization_id, assignee_user_id);

UPDATE tasks SET status = 'IN_PROGRESS' WHERE status = 'BLOCKED';

ALTER TABLE projects ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE users ADD COLUMN super_admin BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE memberships SET role = 'ORG_ADMIN' WHERE role = 'OWNER';
UPDATE memberships SET role = 'PROJECT_MANAGER' WHERE role = 'ADMIN';
UPDATE memberships SET role = 'MEMBER' WHERE role = 'VIEWER';
