ALTER TABLE local_accounts ADD COLUMN account_role TEXT NOT NULL DEFAULT 'owner';

CREATE TABLE IF NOT EXISTS owner_viewers (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  owner_public_id TEXT NOT NULL,
  viewer_nickname TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(owner_public_id, viewer_nickname)
);

CREATE INDEX IF NOT EXISTS idx_owner_viewers_owner
ON owner_viewers(owner_public_id);

CREATE TABLE IF NOT EXISTS message_reads (
  message_id INTEGER NOT NULL,
  owner_public_id TEXT NOT NULL,
  viewer_public_id TEXT NOT NULL,
  viewer_nickname TEXT NOT NULL,
  read_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (message_id, viewer_public_id),
  FOREIGN KEY(message_id) REFERENCES messages(id)
);

CREATE INDEX IF NOT EXISTS idx_message_reads_owner_message
ON message_reads(owner_public_id, message_id);
