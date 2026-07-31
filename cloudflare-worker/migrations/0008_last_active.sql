-- 账号最后活动时间：用于「连续 5 年无活动自动删除」。
-- 活动＝App 记录签到、登录、或在网页状态页查看（自己看别人、别人看自己都算）。
--
-- 注意：SQLite 的 ALTER TABLE ADD COLUMN 不允许非常量默认值（CURRENT_TIMESTAMP 会报
-- "Cannot add a column with non-constant default"），因此先加空字符串列，再回填当前时间。
ALTER TABLE local_accounts ADD COLUMN last_active_at TEXT NOT NULL DEFAULT '';

-- 给现有账号回填当前时间，避免迁移当天就把老账号判为过期。
UPDATE local_accounts SET last_active_at = datetime('now') WHERE last_active_at = '';
