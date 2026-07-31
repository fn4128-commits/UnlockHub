-- 账号邮箱：仅作为「同名同密码」时区分账号的凭据，不做真实性验证、不发送任何邮件。
-- 允许为空以兼容此前已注册的账号。
ALTER TABLE local_accounts ADD COLUMN email TEXT NOT NULL DEFAULT '';
