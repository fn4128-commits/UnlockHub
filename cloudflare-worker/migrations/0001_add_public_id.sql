ALTER TABLE users ADD COLUMN public_id TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_public_id
ON users(public_id);
