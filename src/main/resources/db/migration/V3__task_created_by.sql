ALTER TABLE tasks ADD COLUMN created_by_user_id BIGINT REFERENCES users (id);
CREATE INDEX idx_tasks_created_by ON tasks (created_by_user_id);
