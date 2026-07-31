CREATE TABLE IF NOT EXISTS users (
  device_id TEXT PRIMARY KEY,
  public_id TEXT,
  display_name TEXT NOT NULL,
  guardian_handle TEXT NOT NULL,
  sync_mode TEXT NOT NULL DEFAULT 'weekday',
  sync_weekdays_mask INTEGER NOT NULL DEFAULT 1,
  sync_anchor_date TEXT NOT NULL DEFAULT '',
  sync_interval_days INTEGER NOT NULL DEFAULT 7,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_public_id
ON users(public_id);

CREATE TABLE IF NOT EXISTS unlock_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id TEXT NOT NULL,
  display_name TEXT NOT NULL,
  guardian_handle TEXT NOT NULL,
  local_date TEXT NOT NULL,
  first_unlock_at TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(device_id, local_date)
);

CREATE TABLE IF NOT EXISTS weekly_reports (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id TEXT NOT NULL,
  guardian_handle TEXT NOT NULL,
  week_start TEXT NOT NULL,
  week_end TEXT NOT NULL,
  message_id INTEGER NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(device_id, week_start)
);

CREATE TABLE IF NOT EXISTS inactivity_alerts (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id TEXT NOT NULL,
  guardian_handle TEXT NOT NULL,
  last_activity_at TEXT,
  inactive_hours INTEGER NOT NULL,
  message_id INTEGER NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(device_id, last_activity_at)
);

CREATE TABLE IF NOT EXISTS messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  recipient_handle TEXT NOT NULL,
  sender_device_id TEXT NOT NULL,
  sender_display_name TEXT NOT NULL,
  type TEXT NOT NULL,
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  read_at TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_messages_recipient_created
ON messages(recipient_handle, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_unlock_events_device_date
ON unlock_events(device_id, local_date);

CREATE TABLE IF NOT EXISTS receiver_keys (
  guardian_handle TEXT PRIMARY KEY,
  access_key TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS accounts (
  id TEXT PRIMARY KEY,
  google_sub TEXT NOT NULL UNIQUE,
  email TEXT NOT NULL,
  name TEXT,
  picture TEXT,
  public_id TEXT NOT NULL UNIQUE,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS account_sessions (
  session_id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(account_id) REFERENCES accounts(id)
);

CREATE TABLE IF NOT EXISTS sync_contacts (
  id TEXT PRIMARY KEY,
  owner_account_id TEXT NOT NULL,
  target_public_id TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending',
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(owner_account_id, target_public_id),
  FOREIGN KEY(owner_account_id) REFERENCES accounts(id)
);

CREATE INDEX IF NOT EXISTS idx_account_sessions_account
ON account_sessions(account_id);

CREATE INDEX IF NOT EXISTS idx_sync_contacts_owner
ON sync_contacts(owner_account_id);

CREATE TABLE IF NOT EXISTS local_accounts (
  public_id TEXT PRIMARY KEY,
  nickname TEXT NOT NULL,
  password_salt TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  account_role TEXT NOT NULL DEFAULT 'owner',
  -- 仅用于「同名同密码」时区分账号；不验证真实性、不发邮件。
  email TEXT NOT NULL DEFAULT '',
  -- 最后活动时间：App 签到/登录、网页查看都会刷新；5 年无活动则自动删除账号。
  last_active_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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

CREATE TABLE IF NOT EXISTS memos (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id TEXT NOT NULL,
  guardian_handle TEXT NOT NULL,
  client_id INTEGER NOT NULL,
  title TEXT NOT NULL DEFAULT '',
  content TEXT NOT NULL DEFAULT '',
  type TEXT NOT NULL DEFAULT 'text',
  memo_date TEXT NOT NULL DEFAULT '',
  pinned INTEGER NOT NULL DEFAULT 0,
  done INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(device_id, client_id)
);

CREATE INDEX IF NOT EXISTS idx_memos_guardian
ON memos(guardian_handle);
