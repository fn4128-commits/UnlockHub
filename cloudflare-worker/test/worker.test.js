import assert from "node:assert/strict";
import test from "node:test";

import { addOwnerViewer, changeLocalAccountPassword, deleteLocalAccount, listMessages, purgeInactiveAccounts, setLocalAccountEmail, eligibleReportWeek, getReceiverSummary, handleInactivityAlert, handleTestWeeklyReport, handleUnlockEvent, inboxPage, listMessagesForViewer, loginLocalAccount, markMessageReadForViewer, recoverViewerUid, registerLocalAccount, route, runInactivityMonitor } from "../src/worker.js";

test("eligibleReportWeek uses current week on Sunday", () => {
  const result = eligibleReportWeek(new Date("2026-06-07T00:00:00.000Z"));

  assert.equal(result.weekStart.toISOString().slice(0, 10), "2026-06-01");
  assert.equal(result.weekEnd.toISOString().slice(0, 10), "2026-06-07");
});

test("inbox page includes query form and auth corner", () => {
  const html = inboxPage();

  assert.match(html, /UnlockHub 查询页/);
  assert.match(html, /查询/);
  assert.match(html, /对方 UID/);
  assert.match(html, /authCorner/);
  assert.match(html, /viewerId/);
  assert.match(html, /\/register/);
  assert.match(html, /\/login/);
});

test("app page shows register and login when signed out", async () => {
  const db = new MemoryD1();
  const response = await route(new Request("https://safe.example/app"), { DB: db });
  const html = await response.text();

  assert.equal(response.status, 200);
  assert.match(html, /注册新账号/);
  assert.match(html, /登录已有账号/);
});

test("login page accepts uid and password", async () => {
  const response = await route(new Request("https://safe.example/login"), { DB: new MemoryD1() });
  const html = await response.text();

  assert.equal(response.status, 200);
  assert.match(html, /UnlockHub 登录/);
  assert.match(html, /\/api\/login/);
  assert.match(html, /我的 UID/);
  assert.match(html, /忘记 UID/);
});

test("local registration creates uid and protects status with password", async () => {
  const db = new MemoryD1();
  const account = await registerLocalAccount(db, { nickname: "Alex", email: "t@example.com", password: "secret1234" });

  assert.match(account.publicId, /^SP-/);
  assert.equal(account.nickname, "Alex");

  await assert.rejects(
    () =>
      route(
        new Request(`https://safe.example/api/summary?syncId=${account.publicId}&ownerView=1&accessPassword=wrong`),
        { DB: db }
      ),
    /UID or password is incorrect/
  );

  const response = await route(
    new Request(`https://safe.example/api/summary?syncId=${account.publicId}&ownerView=1&accessPassword=secret1234`),
    { DB: db }
  );
  assert.equal(response.status, 200);
});

test("two users can register independently with different uids", async () => {
  const db = new MemoryD1();
  const first = await registerLocalAccount(db, { nickname: "Alex", email: "t@example.com", password: "secret1234" });
  const second = await registerLocalAccount(db, { nickname: "Jordan", email: "t@example.com", password: "secret2345" });

  assert.notEqual(first.publicId, second.publicId);
  assert.equal(first.nickname, "Alex");
  assert.equal(second.nickname, "Jordan");
});

test("registered accounts require password to sync unlock events", async () => {
  const db = new MemoryD1();
  const account = await registerLocalAccount(db, { nickname: "Alex", email: "t@example.com", password: "secret1234" });

  await assert.rejects(
    () =>
      handleUnlockEvent(db, {
        deviceId: "device-1",
        displayName: "Alex",
        guardianHandle: account.publicId,
        receiverAccessKey: "wrong",
        localDate: "2026-06-01",
        firstUnlockAt: "2026-06-01T08:00:00+08:00",
      }),
    /UID or password is incorrect/
  );

  const result = await handleUnlockEvent(db, {
    deviceId: "device-1",
    displayName: "Alex",
    guardianHandle: account.publicId,
    receiverAccessKey: "secret1234",
    localDate: "2026-06-01",
    firstUnlockAt: "2026-06-01T08:00:00+08:00",
  });

  assert.equal(result.ok, true);
});

test("summary includes owner nickname for registered accounts", async () => {
  const db = new MemoryD1();
  const account = await registerLocalAccount(db, { nickname: "Alex", email: "t@example.com", password: "secret1234" });
  await handleUnlockEvent(db, {
    deviceId: "device-1",
    displayName: "Alex",
    guardianHandle: account.publicId,
    receiverAccessKey: "secret1234",
    localDate: "2026-06-01",
    firstUnlockAt: "2026-06-01T08:00:00+08:00",
  });

  const summary = await getReceiverSummary(db, account.publicId, Date.parse("2026-06-02T08:00:00+08:00"));
  assert.equal(summary.ownerNickname, "Alex");
});

test("change password updates auth for viewing and profile page exists", async () => {
  const db = new MemoryD1();
  const account = await registerLocalAccount(db, { nickname: "Alex", email: "t@example.com", password: "secret1234" });
  await changeLocalAccountPassword(db, {
    publicId: account.publicId,
    currentPassword: "secret1234",
    newPassword: "secret2345",
  });

  await assert.rejects(
    () =>
      route(
        new Request(`https://safe.example/api/summary?syncId=${account.publicId}&ownerView=1&accessPassword=secret1234`),
        { DB: db }
      ),
    /UID or password is incorrect/
  );
  const allowed = await route(
    new Request(`https://safe.example/api/summary?syncId=${account.publicId}&ownerView=1&accessPassword=secret2345`),
    { DB: db }
  );
  assert.equal(allowed.status, 200);

  const profile = await route(new Request("https://safe.example/profile"), { DB: db });
  assert.equal(profile.status, 200);
  assert.match(await profile.text(), /修改密码/);
});

test("local login verifies uid and password", async () => {
  const db = new MemoryD1();
  const account = await registerLocalAccount(db, { nickname: "Alex", email: "t@example.com", password: "secret1234" });
  const loggedIn = await loginLocalAccount(db, { publicId: account.publicId, password: "secret1234" });

  assert.equal(loggedIn.publicId, account.publicId);
  assert.equal(loggedIn.nickname, "Alex");
  await assert.rejects(
    () =>
      route(
        new Request("https://safe.example/api/login", {
          method: "POST",
          body: JSON.stringify({ publicId: account.publicId, password: "wrong" }),
        }),
        { DB: db }
      ),
    /UID 或密码不正确/
  );
});

test("recover uid verifies password + peer + email and delivers to the peer", async () => {
  const db = new MemoryD1();
  const owner = await registerLocalAccount(db, { nickname: "儿子", email: "son@example.com", password: "secret1234", role: "owner" });
  const mom = await registerLocalAccount(db, { nickname: "妈妈", email: "mom@example.com", password: "secret2345", role: "viewer" });
  await addOwnerViewer(db, owner.publicId, "妈妈");

  // 用密码 + 邮箱 + 同步对象找回；不需要记得自己的昵称。
  const recovered = await recoverViewerUid(db, {
    password: "secret2345",
    email: "mom@example.com",
    peerHandle: "儿子",
  });
  assert.equal(recovered.delivered, true);
  assert.equal(recovered.sentTo, "儿子");

  // UID 与昵称应作为消息送到同步对象（儿子）的状态页，而不是直接返回。
  const messages = await listMessages(db, owner.publicId);
  const recoveryMessage = messages.find((m) => m.type === "uid_recovery");
  assert.ok(recoveryMessage, "peer should receive a recovery message");
  assert.ok(recoveryMessage.body.includes(mom.publicId), "message should carry the UID");
  assert.ok(recoveryMessage.body.includes("妈妈"), "message should carry the nickname");

  // 邮箱不对时应失败。
  await assert.rejects(
    () => recoverViewerUid(db, { password: "secret2345", email: "wrong@example.com", peerHandle: "儿子" }),
    /不匹配|not/
  );

  // 与该对象没有关联时应失败。
  await registerLocalAccount(db, { nickname: "陌生人", email: "x@example.com", password: "secret9999", role: "viewer" });
  await assert.rejects(
    () => recoverViewerUid(db, { password: "secret9999", email: "x@example.com", peerHandle: "儿子" }),
    /不匹配|not/
  );
});

test("reinstalled app can sync with same uid on a new device id", async () => {
  const db = new MemoryD1();
  const account = await registerLocalAccount(db, { nickname: "Alex", email: "t@example.com", password: "secret1234" });
  const payload = {
    displayName: "Alex",
    guardianHandle: account.publicId,
    publicId: account.publicId,
    receiverAccessKey: "secret1234",
    syncMode: "weekday",
    syncWeekdaysMask: 4,
  };

  await handleUnlockEvent(db, {
    ...payload,
    deviceId: "device-old",
    localDate: "2026-06-01",
    firstUnlockAt: "2026-06-01T08:00:00+08:00",
  });

  const migrated = await handleUnlockEvent(db, {
    ...payload,
    deviceId: "device-new",
    localDate: "2026-06-02",
    firstUnlockAt: "2026-06-02T08:00:00+08:00",
  });

  assert.equal(migrated.ok, true);
  assert.equal(db.users.size, 1);
  assert.equal(db.users.get("device-new").public_id, account.publicId);
  assert.equal(db.users.has("device-old"), false);
  assert.equal(db.unlockEvents.length, 2);
  assert.ok(db.unlockEvents.every((event) => event.device_id === "device-new"));
});

test("unlock events create one sync report message", async () => {
  const db = new MemoryD1();
  const syncPayload = {
    deviceId: "device-1",
    displayName: "Alex",
    guardianHandle: "mom",
    syncMode: "weekday",
    syncWeekdaysMask: 64,
    syncIntervalDays: 7,
  };

  for (const localDate of ["2026-06-01", "2026-06-02", "2026-06-03", "2026-06-04", "2026-06-05", "2026-06-06", "2026-06-07"]) {
    await handleUnlockEvent(db, {
      ...syncPayload,
      localDate,
      firstUnlockAt: `${localDate}T08:00:00+08:00`,
    });
  }

  assert.equal(db.messages.length, 1);
  assert.equal(db.messages[0].type, "weekly_report");
  assert.match(db.messages[0].body, /2026-06-01 至 2026-06-07/);
});

test("new sync report replaces the previous one", async () => {
  const db = new MemoryD1();
  const syncPayload = {
    deviceId: "device-1",
    displayName: "Alex",
    guardianHandle: "mom",
    syncMode: "weekday",
    syncWeekdaysMask: 64,
    syncIntervalDays: 7,
  };

  const dates = [
    "2026-06-01", "2026-06-02", "2026-06-03", "2026-06-04", "2026-06-05", "2026-06-06", "2026-06-07",
    "2026-06-08", "2026-06-09", "2026-06-10", "2026-06-11", "2026-06-12", "2026-06-13", "2026-06-14",
  ];
  for (const localDate of dates) {
    await handleUnlockEvent(db, {
      ...syncPayload,
      localDate,
      firstUnlockAt: `${localDate}T08:00:00+08:00`,
    });
  }

  const reports = db.messages.filter((message) => message.type === "weekly_report");
  assert.equal(reports.length, 1);
  assert.match(reports[0].body, /2026-06-08 至 2026-06-14/);
});





test("inactivity alerts are deduplicated", async () => {
  const db = new MemoryD1();
  const payload = {
    deviceId: "device-1",
    displayName: "Alex",
    guardianHandle: "mom",
    lastActivityAt: "2026-06-01T08:00:00+08:00",
    inactiveHours: 72,
  };

  const first = await handleInactivityAlert(db, payload);
  const second = await handleInactivityAlert(db, payload);

  assert.equal(first.ok, true);
  assert.equal(second.duplicate, true);
  assert.equal(db.messages.length, 1);
});

test("scheduled monitor creates alert when cloud sees 72 hours of silence", async () => {
  const db = new MemoryD1();
  await handleUnlockEvent(db, {
    deviceId: "device-1",
    displayName: "Alex",
    guardianHandle: "mom",
    localDate: "2026-06-01",
    firstUnlockAt: "2026-06-01T08:00:00+08:00",
    syncMode: "weekday",
    syncWeekdaysMask: 4,
  });

  const result = await runInactivityMonitor(db, Date.parse("2026-06-04T09:00:00+08:00"));
  const second = await runInactivityMonitor(db, Date.parse("2026-06-04T10:00:00+08:00"));

  assert.equal(result.alertsCreated, 1);
  assert.equal(second.alertsCreated, 0);
  assert.equal(db.messages.length, 1);
  assert.equal(db.messages[0].type, "inactivity_alert");
});

test("route returns receiver messages for allowed viewers", async () => {
  const db = new MemoryD1();
  const viewer = await registerLocalAccount(db, { nickname: "妈妈", email: "t@example.com", password: "secret2345", role: "viewer" });
  await addOwnerViewer(db, "mom", "妈妈");
  await handleInactivityAlert(db, {
    deviceId: "device-1",
    displayName: "Alex",
    guardianHandle: "mom",
    lastActivityAt: "2026-06-01T08:00:00+08:00",
    inactiveHours: 72,
  });

  const response = await route(
    new Request(
      `https://safe.example/api/messages?syncId=mom&viewerId=${viewer.publicId}&viewerPassword=secret2345`
    ),
    { DB: db }
  );
  const payload = await response.json();

  assert.equal(response.status, 200);
  assert.equal(payload.messages.length, 1);
  assert.equal(payload.messages[0].type, "inactivity_alert");
});

test("route returns receiver unlock records", async () => {
  const db = new MemoryD1();
  // 记录端首次同步时设置 receiver 访问密钥（TOFU）。
  await handleUnlockEvent(db, {
    deviceId: "device-1",
    displayName: "Alex",
    guardianHandle: "mom",
    receiverAccessKey: "family-key-123",
    localDate: "2026-06-01",
    firstUnlockAt: "2026-06-01T08:00:00+08:00",
  });

  // 无密钥 → 默认拒绝（安全加固：不能仅凭 UID 就读取）。
  await assert.rejects(
    route(new Request("https://safe.example/api/unlock-events?guardianHandle=mom"), { DB: db })
  );

  // 带正确密钥（走请求头）→ 可读。
  const response = await route(new Request("https://safe.example/api/unlock-events?guardianHandle=mom", {
    headers: { "x-access-key": "family-key-123" },
  }), { DB: db });
  const payload = await response.json();

  assert.equal(response.status, 200);
  assert.equal(payload.events.length, 1);
  assert.equal(payload.events[0].local_date, "2026-06-01");
});

test("summary reports active and inactive states", async () => {
  const db = new MemoryD1();
  await handleUnlockEvent(db, {
    deviceId: "device-1",
    displayName: "Alex",
    guardianHandle: "mom",
    localDate: "2026-06-01",
    firstUnlockAt: "2026-06-01T08:00:00+08:00",
  });

  const active = await getReceiverSummary(db, "mom", Date.parse("2026-06-02T08:00:00+08:00"));
  const inactive = await getReceiverSummary(db, "mom", Date.parse("2026-06-04T09:00:00+08:00"));

  assert.equal(active.status, "active");
  assert.equal(active.inactiveHours, 24);
  assert.equal(inactive.status, "inactive_alert");
  assert.equal(inactive.inactiveHours, 73);
});

test("viewer must be on allowlist to read messages", async () => {
  const db = new MemoryD1();
  const owner = await registerLocalAccount(db, { nickname: "儿子", email: "t@example.com", password: "secret1234", role: "owner" });
  const viewer = await registerLocalAccount(db, { nickname: "妈妈", email: "t@example.com", password: "secret2345", role: "viewer" });
  await handleUnlockEvent(db, {
    deviceId: "device-1",
    displayName: "儿子",
    guardianHandle: owner.publicId,
    receiverAccessKey: "secret1234",
    localDate: "2026-06-01",
    firstUnlockAt: "2026-06-01T08:00:00+08:00",
  });

  await assert.rejects(
    () =>
      route(
        new Request(
          `https://safe.example/api/messages?syncId=${owner.publicId}&viewerId=${viewer.publicId}&viewerPassword=secret2345`
        ),
        { DB: db }
      ),
    /未在对方的查看名单中/
  );

  await addOwnerViewer(db, owner.publicId, "妈妈");
  const allowed = await route(
    new Request(
      `https://safe.example/api/messages?syncId=${owner.publicId}&viewerId=${viewer.publicId}&viewerPassword=secret2345`
    ),
    { DB: db }
  );

  assert.equal(allowed.status, 200);
});

test("viewer read status is tracked per nickname", async () => {
  const db = new MemoryD1();
  const owner = await registerLocalAccount(db, { nickname: "儿子", email: "t@example.com", password: "secret1234", role: "owner" });
  const mom = await registerLocalAccount(db, { nickname: "妈妈", email: "t@example.com", password: "secret2345", role: "viewer" });
  await addOwnerViewer(db, owner.publicId, "妈妈");

  await handleUnlockEvent(db, {
    deviceId: "device-1",
    displayName: "儿子",
    guardianHandle: owner.publicId,
    receiverAccessKey: "secret1234",
    localDate: "2026-06-01",
    firstUnlockAt: "2026-06-01T08:00:00+08:00",
  });
  const report = await handleTestWeeklyReport(db, {
    deviceId: "device-1",
    displayName: "儿子",
    guardianHandle: owner.publicId,
    receiverAccessKey: "secret1234",
  });

  const before = await listMessagesForViewer(db, owner.publicId, mom.publicId);
  assert.equal(before[0].read_at, null);

  await markMessageReadForViewer(db, report.messageId, owner.publicId, mom);

  const after = await listMessagesForViewer(db, owner.publicId, mom.publicId);
  assert.ok(after[0].read_at);

  const response = await route(
    new Request(`https://safe.example/api/messages/${report.messageId}/read`, {
      method: "POST",
      body: JSON.stringify({
        syncId: owner.publicId,
        viewerId: mom.publicId,
        viewerPassword: "secret2345",
      }),
    }),
    { DB: db }
  );
  assert.equal(response.status, 200);
});

test("test weekly report creates a visible message from existing records", async () => {
  const db = new MemoryD1();
  await handleUnlockEvent(db, {
    deviceId: "device-1",
    displayName: "Alex",
    guardianHandle: "mom",
    localDate: "2026-06-01",
    firstUnlockAt: "2026-06-01T08:00:00+08:00",
    syncMode: "weekday",
    syncWeekdaysMask: 4,
  });

  const result = await handleTestWeeklyReport(db, {
    deviceId: "device-1",
    displayName: "Alex",
    guardianHandle: "mom",
  });

  assert.equal(result.ok, true);
  assert.equal(db.messages.length, 1);
  assert.equal(db.messages[0].title, "Alex 的测试状态周报");
});

class MemoryD1 {
  constructor() {
    this.users = new Map();
    this.unlockEvents = [];
    this.weeklyReports = [];
    this.inactivityAlerts = [];
    this.messages = [];
    this.receiverKeys = new Map();
    this.accounts = new Map();
    this.localAccounts = new Map();
    this.sessions = new Map();
    this.ownerViewers = [];
    this.messageReads = [];
    this.nextMessageId = 1;
    this.nextOwnerViewerId = 1;
  }

  prepare(sql) {
    return new Statement(this, sql);
  }
}

class Statement {
  constructor(db, sql) {
    this.db = db;
    this.sql = sql.replace(/\s+/g, " ").trim();
    this.values = [];
  }

  bind(...values) {
    this.values = values;
    return this;
  }

  async run() {
    const sql = this.sql;
    const values = this.values;
    if (sql.startsWith("INSERT INTO users")) {
      const publicId = values[1] || null;
      if (publicId) {
        for (const user of this.db.users.values()) {
          if (user.public_id === publicId && user.device_id !== values[0]) {
            throw new Error("UNIQUE constraint failed: users.public_id");
          }
        }
      }
      this.db.users.set(values[0], {
        device_id: values[0],
        public_id: values[1] || null,
        display_name: values[2],
        guardian_handle: values[3],
        sync_mode: values[4] || "weekday",
        sync_weekdays_mask: Number(values[5] || 1),
        sync_anchor_date: values[6] || "",
        sync_interval_days: Number(values[7] || 7),
      });
      return { meta: {} };
    }
    if (sql.startsWith("DELETE FROM users")) {
      this.db.users.delete(values[0]);
      return { meta: {} };
    }
    if (sql.startsWith("UPDATE unlock_events SET device_id")) {
      for (const event of this.db.unlockEvents) {
        if (event.device_id === values[1]) {
          event.device_id = values[0];
        }
      }
      return { meta: {} };
    }
    if (sql.startsWith("UPDATE weekly_reports SET device_id")) {
      for (const report of this.db.weeklyReports) {
        if (report.device_id === values[1]) {
          report.device_id = values[0];
        }
      }
      return { meta: {} };
    }
    if (sql.startsWith("UPDATE inactivity_alerts SET device_id")) {
      for (const alert of this.db.inactivityAlerts) {
        if (alert.device_id === values[1]) {
          alert.device_id = values[0];
        }
      }
      return { meta: {} };
    }
    if (sql.startsWith("UPDATE messages SET sender_device_id")) {
      for (const message of this.db.messages) {
        if (message.sender_device_id === values[1]) {
          message.sender_device_id = values[0];
        }
      }
      return { meta: {} };
    }
    // 整表按设备删除（账号删除/清理用）走后面的分支；这里只处理带 LIMIT 的裁剪。
    if (sql.startsWith("DELETE FROM unlock_events") && sql.includes("LIMIT")) {
      const deviceId = values[0];
      const limit = Number(values[1] || 0);
      const owned = this.db.unlockEvents
        .filter((event) => event.device_id === deviceId)
        .sort((a, b) => a.local_date.localeCompare(b.local_date));
      const removeIds = new Set(owned.slice(0, limit).map((event) => `${event.device_id}:${event.local_date}`));
      this.db.unlockEvents = this.db.unlockEvents.filter(
        (event) => !removeIds.has(`${event.device_id}:${event.local_date}`)
      );
      return { meta: {} };
    }
    if (sql.startsWith("INSERT OR IGNORE INTO unlock_events")) {
      const exists = this.db.unlockEvents.some((event) => event.device_id === values[0] && event.local_date === values[3]);
      if (!exists) {
        this.db.unlockEvents.push({
          device_id: values[0],
          display_name: values[1],
          guardian_handle: values[2],
          local_date: values[3],
          first_unlock_at: values[4],
        });
      }
      return { meta: {} };
    }
    if (sql.startsWith("INSERT INTO messages")) {
      const id = this.db.nextMessageId++;
      this.db.messages.push({
        id,
        recipient_handle: values[0],
        sender_device_id: values[1],
        sender_display_name: values[2],
        type: values[3],
        title: values[4],
        body: values[5],
        read_at: null,
        created_at: "2026-06-07 08:00:00",
      });
      return { meta: { last_row_id: id } };
    }
    if (sql.startsWith("INSERT INTO weekly_reports")) {
      this.db.weeklyReports.push({
        device_id: values[0],
        guardian_handle: values[1],
        week_start: values[2],
        week_end: values[3],
        message_id: values[4],
      });
      return { meta: {} };
    }
    if (sql.startsWith("INSERT INTO inactivity_alerts")) {
      this.db.inactivityAlerts.push({
        device_id: values[0],
        guardian_handle: values[1],
        last_activity_at: values[2],
        inactive_hours: values[3],
        message_id: values[4],
      });
      return { meta: {} };
    }
    if (sql.startsWith("INSERT INTO receiver_keys")) {
      // INSERT ... (guardian_handle, access_key, access_key_hash, access_key_salt, ...) VALUES(?1, '', ?2, ?3, ...)
      this.db.receiverKeys.set(values[0], { access_key: "", access_key_hash: values[1], access_key_salt: values[2] });
      return { meta: {} };
    }
    if (sql.startsWith("INSERT INTO accounts")) {
      const existing = Array.from(this.db.accounts.values()).find((account) => account.google_sub === values[1]);
      const account = existing || {
        id: values[0],
        google_sub: values[1],
        public_id: values[5],
      };
      account.email = values[2];
      account.name = values[3];
      account.picture = values[4];
      this.db.accounts.set(account.id, account);
      return { meta: {} };
    }
    if (sql.startsWith("INSERT INTO account_sessions")) {
      this.db.sessions.set(values[0], {
        session_id: values[0],
        account_id: values[1],
        expires_at: values[2],
      });
      return { meta: {} };
    }
    if (sql.startsWith("INSERT INTO local_accounts")) {
      if (this.db.localAccounts.has(values[0])) {
        throw new Error("UNIQUE constraint failed");
      }
      this.db.localAccounts.set(values[0], {
        public_id: values[0],
        nickname: values[1],
        password_salt: values[2],
        password_hash: values[3],
        account_role: values[4] || "owner",
        email: values[5] || "",
        last_active_at: new Date().toISOString().replace("T", " ").slice(0, 19),
      });
      return { meta: {} };
    }
    if (sql.startsWith("UPDATE local_accounts SET last_active_at")) {
      const account = this.db.localAccounts.get(values[0]);
      if (account) account.last_active_at = new Date().toISOString().replace("T", " ").slice(0, 19);
      return { meta: {} };
    }
    if (sql.startsWith("UPDATE local_accounts SET email")) {
      const account = this.db.localAccounts.get(values[1]);
      if (account) account.email = values[0];
      return { meta: {} };
    }
    if (sql.startsWith("DELETE FROM local_accounts WHERE public_id")) {
      this.db.localAccounts.delete(values[0]);
      return { meta: {} };
    }
    if (sql.startsWith("DELETE FROM users WHERE public_id")) {
      for (const [key, user] of Array.from(this.db.users.entries())) {
        if (user.public_id === values[0]) this.db.users.delete(key);
      }
      return { meta: {} };
    }
    if (sql.startsWith("DELETE FROM unlock_events WHERE device_id")) {
      this.db.unlockEvents = (this.db.unlockEvents || []).filter((e) => e.device_id !== values[0]);
      return { meta: {} };
    }
    if (sql === "DELETE FROM messages WHERE recipient_handle = ?1") {
      this.db.messages = this.db.messages.filter((m) => m.recipient_handle !== values[0]);
      return { meta: {} };
    }
    if (sql.startsWith("DELETE FROM owner_viewers WHERE owner_public_id")) {
      this.db.ownerViewers = this.db.ownerViewers.filter((v) => v.owner_public_id !== values[0]);
      return { meta: {} };
    }
    if (sql.startsWith("DELETE FROM receiver_keys WHERE guardian_handle")) {
      this.db.receiverKeys.delete(values[0]);
      return { meta: {} };
    }
    if (sql.startsWith("INSERT INTO owner_viewers")) {
      const duplicate = this.db.ownerViewers.some(
        (item) => item.owner_public_id === values[0] && item.viewer_nickname === values[1]
      );
      if (duplicate) {
        throw new Error("UNIQUE constraint failed");
      }
      const id = this.db.nextOwnerViewerId++;
      this.db.ownerViewers.push({
        id,
        owner_public_id: values[0],
        viewer_nickname: values[1],
        created_at: "2026-06-07 08:00:00",
        updated_at: "2026-06-07 08:00:00",
      });
      return { meta: { last_row_id: id } };
    }
    if (sql.startsWith("INSERT INTO message_reads")) {
      const [messageId, ownerPublicId, viewerPublicId, viewerNickname] = values;
      const existing = this.db.messageReads.find(
        (row) => row.message_id === messageId && row.viewer_public_id === viewerPublicId
      );
      if (existing) {
        if (!existing.read_at) {
          existing.read_at = "2026-06-07 08:10:00";
        }
        existing.viewer_nickname = viewerNickname;
      } else {
        this.db.messageReads.push({
          message_id: messageId,
          owner_public_id: ownerPublicId,
          viewer_public_id: viewerPublicId,
          viewer_nickname: viewerNickname,
          read_at: "2026-06-07 08:10:00",
        });
      }
      return { meta: {} };
    }
    if (sql.startsWith("UPDATE owner_viewers")) {
      const viewer = this.db.ownerViewers.find((item) => item.id === values[0] && item.owner_public_id === values[1]);
      if (!viewer) {
        return { meta: { changes: 0 } };
      }
      viewer.viewer_nickname = values[2];
      viewer.updated_at = "2026-06-07 08:10:00";
      return { meta: { changes: 1 } };
    }
    if (sql.startsWith("DELETE FROM owner_viewers")) {
      const index = this.db.ownerViewers.findIndex((item) => item.id === values[0] && item.owner_public_id === values[1]);
      if (index < 0) {
        return { meta: { changes: 0 } };
      }
      this.db.ownerViewers.splice(index, 1);
      return { meta: { changes: 1 } };
    }
    if (sql.startsWith("UPDATE local_accounts")) {
      const account = this.db.localAccounts.get(values[0]);
      if (!account) throw new Error("Account not found");
      account.password_salt = values[1];
      account.password_hash = values[2];
      return { meta: {} };
    }
    if (sql.startsWith("UPDATE receiver_keys")) {
      // UPDATE ... SET ... access_key_hash = ?2, access_key_salt = ?3 ... WHERE guardian_handle = ?1
      this.db.receiverKeys.set(values[0], { access_key: "", access_key_hash: values[1], access_key_salt: values[2] });
      return { meta: {} };
    }
    if (sql.startsWith("DELETE FROM account_sessions")) {
      this.db.sessions.delete(values[0]);
      return { meta: {} };
    }
    if (sql.startsWith("DELETE FROM memos")) {
      this.db.memos = (this.db.memos || []).filter((memo) => memo.device_id !== values[0]);
      return { meta: {} };
    }
    if (sql.startsWith("INSERT INTO memos")) {
      this.db.memos = this.db.memos || [];
      this.db.memos.push({
        device_id: values[0],
        guardian_handle: values[1],
        client_id: values[2],
        title: values[3],
        content: values[4],
        type: values[5],
        memo_date: values[6],
        pinned: values[7],
        done: values[8],
        updated_at: values[9],
      });
      return { meta: {} };
    }
    if (sql.startsWith("UPDATE memos SET device_id")) {
      for (const memo of this.db.memos || []) {
        if (memo.device_id === values[1]) {
          memo.device_id = values[0];
        }
      }
      return { meta: {} };
    }
    if (sql.startsWith("DELETE FROM message_reads")) {
      const removeIds = new Set(
        this.db.messages
          .filter(
            (message) =>
              message.recipient_handle === values[0] &&
              message.sender_device_id === values[1] &&
              message.type === "weekly_report" &&
              message.id !== values[2]
          )
          .map((message) => message.id)
      );
      this.db.messageReads = this.db.messageReads.filter((row) => !removeIds.has(row.message_id));
      return { meta: {} };
    }
    if (sql.startsWith("DELETE FROM messages")) {
      this.db.messages = this.db.messages.filter(
        (message) =>
          !(
            message.recipient_handle === values[0] &&
            message.sender_device_id === values[1] &&
            message.type === "weekly_report" &&
            message.id !== values[2]
          )
      );
      return { meta: {} };
    }
    if (sql.startsWith("UPDATE messages SET read_at") && sql.includes("recipient_handle")) {
      const message = this.db.messages.find((item) => item.id === values[0] && item.recipient_handle === values[1]);
      if (message && !message.read_at) {
        message.read_at = "2026-06-07 08:10:00";
      }
      return { meta: {} };
    }
    if (sql.startsWith("UPDATE messages SET read_at")) {
      const message = this.db.messages.find((item) => item.id === values[0]);
      if (message && !message.read_at) {
        message.read_at = "2026-06-07 08:10:00";
      }
      return { meta: {} };
    }
    throw new Error(`Unhandled run SQL: ${sql}`);
  }

  async first() {
    const sql = this.sql;
    const values = this.values;
    if (sql.startsWith("SELECT device_id FROM users") && sql.includes("device_id <>")) {
      const deviceId = values[0];
      const lookupId = String(values[1] || "").toUpperCase();
      const user = Array.from(this.db.users.values()).find((item) => {
        if (item.device_id === deviceId) {
          return false;
        }
        const publicId = String(item.public_id || "").toUpperCase();
        const guardianHandle = String(item.guardian_handle || "").toUpperCase();
        return publicId === lookupId || guardianHandle === lookupId;
      });
      return user ? { device_id: user.device_id } : null;
    }
    if (sql.startsWith("SELECT id FROM weekly_reports")) {
      return this.db.weeklyReports.find((report) => report.device_id === values[0] && report.week_start === values[1]) || null;
    }
    if (sql.startsWith("SELECT sync_mode, sync_weekdays_mask, sync_anchor_date, sync_interval_days FROM users")) {
      const user = this.db.users.get(values[0]);
      return user || null;
    }
    if (sql.startsWith("SELECT COUNT(*) AS total FROM unlock_events")) {
      const total = this.db.unlockEvents.filter((event) => event.device_id === values[0]).length;
      return { total };
    }
    if (sql.startsWith("SELECT id FROM inactivity_alerts")) {
      return this.db.inactivityAlerts.find((alert) => alert.device_id === values[0] && alert.last_activity_at === values[1]) || null;
    }
    if (sql.startsWith("SELECT first_unlock_at FROM unlock_events")) {
      const event = this.db.unlockEvents
        .filter((item) => item.device_id === values[0])
        .sort((a, b) => b.first_unlock_at.localeCompare(a.first_unlock_at))[0];
      return event ? { first_unlock_at: event.first_unlock_at } : null;
    }
    if (sql.startsWith("SELECT device_id, display_name, local_date")) {
      const event = this.db.unlockEvents
        .filter((item) => item.guardian_handle === values[0])
        .sort((a, b) => b.first_unlock_at.localeCompare(a.first_unlock_at))[0];
      return event
        ? {
            device_id: event.device_id,
            display_name: event.display_name,
            local_date: event.local_date,
            first_unlock_at: event.first_unlock_at,
            created_at: "2026-06-07 08:00:00",
          }
        : null;
    }
    if (sql.startsWith("SELECT COUNT(*) AS total_messages")) {
      const messages = this.db.messages.filter((message) => message.recipient_handle === values[0]);
      return {
        total_messages: messages.length,
      };
    }
    if (sql.startsWith("SELECT COUNT(*) AS unread_messages")) {
      const ownerHandle = values[0];
      const viewerPublicId = values[1];
      const unread = this.db.messages.filter((message) => {
        if (message.recipient_handle !== ownerHandle) return false;
        const read = this.db.messageReads.find(
          (row) =>
            row.message_id === message.id &&
            row.owner_public_id === ownerHandle &&
            row.viewer_public_id === viewerPublicId &&
            row.read_at
        );
        return !read;
      }).length;
      return { unread_messages: unread };
    }
    if (sql.startsWith("SELECT id FROM messages WHERE id = ?1 AND recipient_handle = ?2")) {
      const message = this.db.messages.find((item) => item.id === values[0] && item.recipient_handle === values[1]);
      return message ? { id: message.id } : null;
    }
    if (sql.startsWith("SELECT public_id, nickname, account_role FROM local_accounts") && sql.includes("account_role = 'owner'")) {
      const account = Array.from(this.db.localAccounts.values()).find(
        (item) => item.nickname.toLowerCase() === String(values[0]).toLowerCase() && item.account_role === "owner"
      );
      return account ? { public_id: account.public_id, nickname: account.nickname, account_role: account.account_role } : null;
    }
    if (sql.startsWith("SELECT id FROM owner_viewers")) {
      const allowed = this.db.ownerViewers.find(
        (item) =>
          item.owner_public_id === values[0] &&
          item.viewer_nickname.toLowerCase() === String(values[1]).toLowerCase()
      );
      return allowed ? { id: allowed.id } : null;
    }
    if (sql.startsWith("SELECT access_key, access_key_hash, access_key_salt FROM receiver_keys")) {
      const row = this.db.receiverKeys.get(values[0]);
      return row || null;
    }
    if (sql.startsWith("SELECT nickname FROM local_accounts")) {
      const account = this.db.localAccounts.get(values[0]);
      return account ? { nickname: account.nickname } : null;
    }
    if (sql.startsWith("SELECT password_salt, password_hash FROM local_accounts")) {
      const account = this.db.localAccounts.get(values[0]);
      return account ? { password_salt: account.password_salt, password_hash: account.password_hash } : null;
    }
    if (sql.startsWith("SELECT public_id, nickname, password_salt, password_hash FROM local_accounts")) {
      const account = this.db.localAccounts.get(values[0]);
      return account || null;
    }
    if (sql.startsWith("SELECT public_id, nickname, password_salt, password_hash, account_role") &&
        sql.includes("FROM local_accounts WHERE public_id")) {
      const account = this.db.localAccounts.get(values[0]);
      if (!account) return null;
      // 只返回 SQL 里点名的列：真实 D1 不会给出未 SELECT 的字段，
      // 这样「忘记 SELECT email」这类 bug 才会在测试里暴露出来。
      const picked = {};
      for (const column of ["public_id", "nickname", "password_salt", "password_hash", "account_role", "email", "last_active_at"]) {
        if (sql.includes(column)) picked[column] = account[column];
      }
      return picked;
    }
    if (sql.includes("FROM local_accounts") && sql.includes("lower(nickname) = lower(?1)")) {
      const wanted = String(values[0] || "").toLowerCase();
      const rows = Array.from(this.db.localAccounts.values())
        .filter((a) => String(a.nickname || "").toLowerCase() === wanted)
        .filter((a) => (sql.includes("account_role = 'owner'") ? (a.account_role || "owner") === "owner" : true));
      return rows[0] || null;
    }
    if (sql.startsWith("SELECT id, public_id FROM accounts")) {
      return Array.from(this.db.accounts.values()).find((account) => account.google_sub === values[0]) || null;
    }
    if (sql.startsWith("SELECT id, email, name, picture, public_id FROM accounts")) {
      return Array.from(this.db.accounts.values()).find((account) => account.google_sub === values[0]) || null;
    }
    if (sql.startsWith("SELECT accounts.id")) {
      const session = this.db.sessions.get(values[0]);
      if (!session || session.expires_at <= values[1]) return null;
      return this.db.accounts.get(session.account_id) || null;
    }
    throw new Error(`Unhandled first SQL: ${sql}`);
  }

  async all() {
    const sql = this.sql;
    const values = this.values;
    if (sql.startsWith("SELECT public_id FROM local_accounts WHERE last_active_at <")) {
      const cutoff = String(values[0] || "");
      const results = Array.from(this.db.localAccounts.values())
        .filter((a) => String(a.last_active_at || "") < cutoff)
        .map((a) => ({ public_id: a.public_id }));
      return { results };
    }
    if (sql.startsWith("SELECT device_id FROM users WHERE public_id")) {
      const results = Array.from(this.db.users.values())
        .filter((u) => u.public_id === values[0])
        .map((u) => ({ device_id: u.device_id }));
      return { results };
    }
    if (sql.includes("FROM local_accounts") && sql.includes("lower(email) = lower(?1)")) {
      const wanted = String(values[0] || "").toLowerCase();
      const results = Array.from(this.db.localAccounts.values())
        .filter((a) => String(a.email || "").toLowerCase() === wanted);
      return { results };
    }
    if (
      sql.startsWith("SELECT local_date, first_unlock_at FROM unlock_events") &&
      sql.includes("ORDER BY local_date ASC") &&
      !sql.includes("BETWEEN")
    ) {
      const results = this.db.unlockEvents
        .filter((event) => event.device_id === values[0])
        .sort((a, b) => a.local_date.localeCompare(b.local_date))
        .map((event) => ({ local_date: event.local_date, first_unlock_at: event.first_unlock_at }));
      return { results };
    }
    if (sql.startsWith("SELECT local_date, first_unlock_at FROM unlock_events") && sql.includes("BETWEEN")) {
      const results = this.db.unlockEvents
        .filter((event) => event.device_id === values[0] && event.local_date >= values[1] && event.local_date <= values[2])
        .sort((a, b) => a.local_date.localeCompare(b.local_date))
        .map((event) => ({ local_date: event.local_date, first_unlock_at: event.first_unlock_at }));
      return { results };
    }
    if (sql.startsWith("SELECT device_id, display_name, guardian_handle FROM users")) {
      return { results: Array.from(this.db.users.values()) };
    }
    if (sql.startsWith("SELECT local_date, first_unlock_at FROM unlock_events") && sql.includes("LIMIT 7")) {
      const results = this.db.unlockEvents
        .filter((event) => event.device_id === values[0])
        .sort((a, b) => b.local_date.localeCompare(a.local_date))
        .slice(0, 7)
        .map((event) => ({ local_date: event.local_date, first_unlock_at: event.first_unlock_at }));
      return { results };
    }
    if (sql.startsWith("SELECT device_id, display_name, local_date")) {
      const results = this.db.unlockEvents
        .filter((event) => event.guardian_handle === values[0])
        .sort((a, b) => b.local_date.localeCompare(a.local_date))
        .slice(0, 30)
        .map((event) => ({
          device_id: event.device_id,
          display_name: event.display_name,
          local_date: event.local_date,
          first_unlock_at: event.first_unlock_at,
          created_at: "2026-06-07 08:00:00",
        }));
      return { results };
    }
    if (sql.includes("FROM local_accounts") && sql.includes("lower(nickname)")) {
      const results = Array.from(this.db.localAccounts.values()).filter(
        (account) => account.nickname.toLowerCase() === String(values[0]).toLowerCase()
      );
      return { results };
    }
    if (sql.includes("FROM messages m") && sql.includes("message_reads mr")) {
      const ownerHandle = values[0];
      const viewerPublicId = values[1];
      const results = this.db.messages
        .filter((message) => message.recipient_handle === ownerHandle)
        .sort((a, b) => b.id - a.id)
        .slice(0, 100)
        .map((message) => {
          const read = this.db.messageReads.find(
            (row) =>
              row.message_id === message.id &&
              row.viewer_public_id === viewerPublicId &&
              row.owner_public_id === ownerHandle
          );
          return {
            id: message.id,
            sender_device_id: message.sender_device_id,
            sender_display_name: message.sender_display_name,
            type: message.type,
            title: message.title,
            body: message.body,
            created_at: message.created_at,
            read_at: read?.read_at || null,
          };
        });
      return { results };
    }
    if (sql.startsWith("SELECT id, sender_device_id, sender_display_name, type, title, body, created_at FROM messages")) {
      const results = this.db.messages
        .filter((message) => message.recipient_handle === values[0])
        .sort((a, b) => b.id - a.id)
        .slice(0, 100)
        .map((message) => ({
          id: message.id,
          sender_device_id: message.sender_device_id,
          sender_display_name: message.sender_display_name,
          type: message.type,
          title: message.title,
          body: message.body,
          created_at: message.created_at,
        }));
      return { results };
    }
    if (sql.startsWith("SELECT client_id, title, content, type, memo_date, pinned, done, updated_at FROM memos")) {
      const results = (this.db.memos || [])
        .filter((memo) => memo.guardian_handle === values[0])
        .sort((a, b) => (b.pinned - a.pinned) || (a.done - b.done) || (b.updated_at - a.updated_at))
        .slice(0, 200)
        .map((memo) => ({
          client_id: memo.client_id,
          title: memo.title,
          content: memo.content,
          type: memo.type,
          memo_date: memo.memo_date,
          pinned: memo.pinned,
          done: memo.done,
          updated_at: memo.updated_at,
        }));
      return { results };
    }
    if (sql.startsWith("SELECT message_id, viewer_public_id, viewer_nickname, read_at FROM message_reads")) {
      const results = this.db.messageReads.filter((row) => row.owner_public_id === values[0]);
      return { results };
    }
    if (sql.includes("FROM owner_viewers ov") && sql.includes("local_accounts la")) {
      const results = this.db.ownerViewers
        .filter((item) => item.owner_public_id === values[0])
        .sort((a, b) => a.viewer_nickname.localeCompare(b.viewer_nickname, undefined, { sensitivity: "base" }) || a.id - b.id)
        .map((item) => {
          const account = Array.from(this.db.localAccounts.values()).find(
            (entry) =>
              entry.account_role === "viewer" &&
              entry.nickname.toLowerCase() === item.viewer_nickname.toLowerCase()
          );
          return {
            id: item.id,
            viewer_nickname: item.viewer_nickname,
            created_at: item.created_at,
            updated_at: item.updated_at,
            viewer_public_id: account ? account.public_id : null,
          };
        });
      return { results };
    }
    if (sql.startsWith("SELECT id, viewer_nickname, created_at, updated_at FROM owner_viewers")) {
      const results = this.db.ownerViewers
        .filter((item) => item.owner_public_id === values[0])
        .sort((a, b) => a.viewer_nickname.localeCompare(b.viewer_nickname, undefined, { sensitivity: "base" }) || a.id - b.id);
      return { results };
    }
    if (sql.startsWith("SELECT id, sender_display_name")) {
      const results = this.db.messages
        .filter((message) => message.recipient_handle === values[0])
        .sort((a, b) => b.id - a.id)
        .map((message) => ({
          id: message.id,
          sender_display_name: message.sender_display_name,
          type: message.type,
          title: message.title,
          body: message.body,
          read_at: message.read_at,
          created_at: message.created_at,
        }));
      return { results };
    }
    throw new Error(`Unhandled all SQL: ${sql}`);
  }
}

test("old accounts without an email are asked to add one, and can", async () => {
  const db = new MemoryD1();
  const account = await registerLocalAccount(db, { nickname: "Old", email: "old@example.com", password: "secret1234" });
  // 模拟迁移前的老账号：邮箱为空
  db.localAccounts.get(account.publicId).email = "";

  const loggedIn = await loginLocalAccount(db, { publicId: account.publicId, password: "secret1234" });
  assert.equal(loggedIn.needsEmail, true, "old account should be flagged");

  await setLocalAccountEmail(db, {
    publicId: account.publicId,
    password: "secret1234",
    email: "filled@example.com",
  });

  const after = await loginLocalAccount(db, { publicId: account.publicId, password: "secret1234" });
  assert.equal(after.needsEmail, false, "flag clears once the email is set");

  // 已有邮箱时不允许再改（此接口只用于补填）
  await assert.rejects(
    () => setLocalAccountEmail(db, { publicId: account.publicId, password: "secret1234", email: "x@example.com" }),
    /已设置邮箱/
  );
});

test("accounts inactive for five years are purged, active ones are kept", async () => {
  const db = new MemoryD1();
  const stale = await registerLocalAccount(db, { nickname: "Stale", email: "stale@example.com", password: "secret1234" });
  const fresh = await registerLocalAccount(db, { nickname: "Fresh", email: "fresh@example.com", password: "secret1234" });

  // 把一个账号的最后活动时间挪到 6 年前
  db.localAccounts.get(stale.publicId).last_active_at = "2020-01-01 00:00:00";

  const result = await purgeInactiveAccounts(db, Date.parse("2026-07-30T00:00:00Z"));
  assert.equal(result.purged, 1);
  assert.equal(db.localAccounts.has(stale.publicId), false, "stale account is gone");
  assert.equal(db.localAccounts.has(fresh.publicId), true, "active account is kept");
});

test("email set at registration is not lost or overwritable", async () => {
  const db = new MemoryD1();
  const account = await registerLocalAccount(db, {
    nickname: "Keeper",
    email: "kept@example.com",
    password: "secret1234",
  });

  // 登录时必须读得到邮箱，否则会把新账号误判成「需要补填」。
  const loggedIn = await loginLocalAccount(db, { publicId: account.publicId, password: "secret1234" });
  assert.equal(loggedIn.needsEmail, false, "a freshly registered account already has an email");

  // 补填接口只用于没有邮箱的老账号，绝不能覆盖已有邮箱。
  await assert.rejects(
    () => setLocalAccountEmail(db, {
      publicId: account.publicId,
      password: "secret1234",
      email: "attacker@example.com",
    }),
    /已设置邮箱/
  );
  assert.equal(db.localAccounts.get(account.publicId).email, "kept@example.com");
});

test("users can delete their own account and all of its data", async () => {
  const db = new MemoryD1();
  const account = await registerLocalAccount(db, { nickname: "Leaver", email: "bye@example.com", password: "secret1234" });
  await addOwnerViewer(db, account.publicId, "妈妈");
  await handleUnlockEvent(db, {
    deviceId: "device-leaver",
    displayName: "Leaver",
    guardianHandle: account.publicId,
    publicId: account.publicId,
    receiverAccessKey: "secret1234",
    localDate: "2026-06-01",
    firstUnlockAt: "2026-06-01T08:00:00+08:00",
  });

  // 密码不对时不得删除
  await assert.rejects(
    () => deleteLocalAccount(db, { publicId: account.publicId, password: "wrong" }),
    /不正确/
  );
  assert.equal(db.localAccounts.has(account.publicId), true, "account survives a wrong password");

  const result = await deleteLocalAccount(db, { publicId: account.publicId, password: "secret1234" });
  assert.equal(result.ok, true);

  // 账号与其关联数据都应清除，不留「曾经存在」的痕迹
  assert.equal(db.localAccounts.has(account.publicId), false, "account row is gone");
  assert.equal(db.ownerViewers.some((v) => v.owner_public_id === account.publicId), false, "viewer list is gone");
  assert.equal((db.unlockEvents || []).some((e) => e.device_id === "device-leaver"), false, "unlock records are gone");

  // 删除后可以用同样的昵称重新注册
  const again = await registerLocalAccount(db, { nickname: "Leaver", email: "bye@example.com", password: "secret1234" });
  assert.notEqual(again.publicId, account.publicId, "a fresh UID is issued");
});

test("an account can be deleted without logging in, using nickname + email + password", async () => {
  const db = new MemoryD1();
  const account = await registerLocalAccount(db, { nickname: "Gone", email: "gone@example.com", password: "secret1234" });

  // 邮箱不对 → 拒绝
  await assert.rejects(
    () => deleteLocalAccount(db, { nickname: "Gone", email: "wrong@example.com", password: "secret1234" }),
    /不正确/
  );
  // 密码不对 → 拒绝
  await assert.rejects(
    () => deleteLocalAccount(db, { nickname: "Gone", email: "gone@example.com", password: "wrong" }),
    /不正确/
  );
  assert.equal(db.localAccounts.has(account.publicId), true, "account survives failed attempts");

  // 三项都对 → 删除（无需 UID）
  const result = await deleteLocalAccount(db, {
    nickname: "Gone",
    email: "gone@example.com",
    password: "secret1234",
  });
  assert.equal(result.ok, true);
  assert.equal(db.localAccounts.has(account.publicId), false, "account is deleted");
});

test("delete is refused when nickname, email and password all match several accounts", async () => {
  const db = new MemoryD1();
  await registerLocalAccount(db, { nickname: "Twin", email: "twin@example.com", password: "secret1234" });
  await registerLocalAccount(db, { nickname: "Twin", email: "twin@example.com", password: "secret1234" });

  // 无法判断该删哪一个时必须拒绝，以免误删他人账号。
  await assert.rejects(
    () => deleteLocalAccount(db, { nickname: "Twin", email: "twin@example.com", password: "secret1234" }),
    /多个账号/
  );
  assert.equal(db.localAccounts.size, 2, "both accounts are kept");
});
