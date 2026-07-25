const JSON_HEADERS = { "content-type": "application/json; charset=utf-8" };
const HTML_HEADERS = { "content-type": "text/html; charset=utf-8" };
const SESSION_COOKIE = "sp_session";
const OAUTH_STATE_COOKIE = "sp_oauth_state";
const SESSION_TTL_SECONDS = 60 * 60 * 24 * 30;

export default {
  async fetch(request, env) {
    try {
      return await route(request, env);
    } catch (error) {
      const status = error instanceof TooManyRequests ? 429
        : error instanceof BadRequest ? 400
        : 500;
      return json({ error: error.message || "Internal error" }, status);
    }
  },
  async scheduled(controller, env) {
    await runInactivityMonitor(env.DB, Date.now());
  },
};

export async function route(request, env) {
  const url = new URL(request.url);
  await ensureSecuritySchema(env.DB);

  // 认证类接口按 IP 限流（防暴力破解）：每 5 分钟最多 15 次。
  if (request.method === "POST" && (
    url.pathname === "/api/register" ||
    url.pathname === "/api/login" ||
    url.pathname === "/api/recover-uid" ||
    url.pathname === "/api/change-password"
  )) {
    await checkRateLimit(env.DB, clientBucket(request, "auth"), 15, 300);
  }

  if (request.method === "GET" && url.pathname === "/") {
    return html(inboxPage());
  }

  if (request.method === "GET" && url.pathname === "/register") {
    return html(registerPage());
  }

  if (request.method === "GET" && url.pathname === "/login") {
    return html(loginPage());
  }

  if (request.method === "GET" && url.pathname === "/forgot-uid") {
    return html(forgotUidPage());
  }

  if (request.method === "POST" && url.pathname === "/api/register") {
    const payload = await readJson(request);
    const account = await registerLocalAccount(env.DB, payload);
    return json({ account });
  }

  if (request.method === "POST" && url.pathname === "/api/login") {
    const payload = await readJson(request);
    const account = await loginLocalAccount(env.DB, payload);
    return json({ account });
  }

  if (request.method === "POST" && url.pathname === "/api/recover-uid") {
    const payload = await readJson(request);
    const account = await recoverViewerUid(env.DB, payload);
    return json({ account });
  }

  if (request.method === "POST" && url.pathname === "/api/change-password") {
    const payload = await readJson(request);
    const account = await changeLocalAccountPassword(env.DB, payload);
    return json({ account });
  }

  if (request.method === "GET" && url.pathname === "/profile") {
    return html(profilePage());
  }

  if (request.method === "GET" && url.pathname === "/app") {
    const account = await getCurrentAccount(env.DB, request);
    return html(appPage(account));
  }

  if (request.method === "GET" && url.pathname === "/auth/google") {
    return startGoogleLogin(request, env);
  }

  if (request.method === "GET" && url.pathname === "/auth/google/callback") {
    return finishGoogleLogin(request, env);
  }

  if (request.method === "POST" && url.pathname === "/auth/logout") {
    const sessionId = getCookie(request, SESSION_COOKIE);
    if (sessionId) {
      await env.DB.prepare("DELETE FROM account_sessions WHERE session_id = ?1").bind(sessionId).run();
    }
    return redirect("/app", [clearCookie(SESSION_COOKIE)]);
  }

  if (request.method === "GET" && url.pathname === "/api/me") {
    const account = await requireCurrentAccount(env.DB, request);
    return json({ account });
  }

  if (request.method === "GET" && url.pathname === "/health") {
    return json({ ok: true });
  }

  if (request.method === "GET" && url.pathname === "/api/messages") {
    const ownerHandle = syncIdFromUrl(url).trim();
    if (!ownerHandle) {
      throw new BadRequest("syncId is required");
    }
    const ownerView = url.searchParams.get("ownerView") === "1";
    if (ownerView) {
      await requireReceiverAccess(env.DB, ownerHandle, accessPasswordFrom(request, url).trim());
      const messages = await listMessagesForOwner(env.DB, ownerHandle);
      return json({ messages });
    }
    const viewer = await requireViewerViewAccess(env.DB, ownerHandle, viewAuthFrom(request, url));
    const messages = await listMessagesForViewer(env.DB, ownerHandle, viewer.publicId);
    return json({ messages });
  }

  if (request.method === "GET" && url.pathname === "/api/unlock-events") {
    const ownerHandle = syncIdFromUrl(url).trim();
    if (!ownerHandle) {
      throw new BadRequest("syncId is required");
    }
    await requireViewAccess(env.DB, ownerHandle, viewAuthFrom(request, url));
    const events = await listUnlockEvents(env.DB, ownerHandle);
    return json({ events });
  }

  if (request.method === "GET" && url.pathname === "/api/summary") {
    const ownerHandle = syncIdFromUrl(url).trim();
    if (!ownerHandle) {
      throw new BadRequest("syncId is required");
    }
    const ownerView = url.searchParams.get("ownerView") === "1";
    if (ownerView) {
      await requireReceiverAccess(env.DB, ownerHandle, accessPasswordFrom(request, url).trim());
      const summary = await getReceiverSummary(env.DB, ownerHandle, Date.now());
      return json({ summary });
    }
    const viewer = await requireViewerViewAccess(env.DB, ownerHandle, viewAuthFrom(request, url));
    const summary = await getViewerSummary(env.DB, ownerHandle, viewer.publicId, Date.now());
    return json({ summary });
  }

  if (request.method === "GET" && url.pathname === "/api/viewers") {
    const ownerHandle = syncIdFromUrl(url).trim();
    if (!ownerHandle) {
      throw new BadRequest("syncId is required");
    }
    await requireReceiverAccess(env.DB, ownerHandle, accessPasswordFrom(request, url).trim());
    const viewers = await listOwnerViewers(env.DB, ownerHandle);
    return json({ viewers });
  }

  if (request.method === "POST" && url.pathname === "/api/viewers") {
    const payload = await readJson(request);
    const ownerHandle = String(payload.syncId || payload.publicId || "").trim().toUpperCase();
    const accessKey = String(payload.accessPassword || payload.receiverAccessKey || payload.password || "");
    if (!ownerHandle) {
      throw new BadRequest("syncId is required");
    }
    await requireReceiverAccess(env.DB, ownerHandle, accessKey);
    const viewer = await addOwnerViewer(env.DB, ownerHandle, payload.nickname);
    return json({ viewer });
  }

  const viewerMatch = url.pathname.match(/^\/api\/viewers\/(\d+)$/);
  if (viewerMatch && request.method === "PUT") {
    const payload = await readJson(request);
    const ownerHandle = String(payload.syncId || payload.publicId || "").trim().toUpperCase();
    const accessKey = String(payload.accessPassword || payload.receiverAccessKey || payload.password || "");
    if (!ownerHandle) {
      throw new BadRequest("syncId is required");
    }
    await requireReceiverAccess(env.DB, ownerHandle, accessKey);
    const viewer = await updateOwnerViewer(env.DB, ownerHandle, Number(viewerMatch[1]), payload.nickname);
    return json({ viewer });
  }

  if (viewerMatch && request.method === "DELETE") {
    const ownerHandle = syncIdFromUrl(url).trim();
    const accessKey = accessPasswordFrom(request, url).trim();
    if (!ownerHandle) {
      throw new BadRequest("syncId is required");
    }
    await requireReceiverAccess(env.DB, ownerHandle, accessKey);
    await deleteOwnerViewer(env.DB, ownerHandle, Number(viewerMatch[1]));
    return json({ ok: true });
  }

  if (request.method === "POST" && url.pathname === "/api/unlock-events") {
    const payload = await readJson(request);
    const result = await handleUnlockEvent(env.DB, payload);
    return json(result);
  }

  if (request.method === "POST" && url.pathname === "/api/inactivity-alerts") {
    const payload = await readJson(request);
    const result = await handleInactivityAlert(env.DB, payload);
    return json(result);
  }

  if (request.method === "POST" && url.pathname === "/api/custom-alerts") {
    const payload = await readJson(request);
    const result = await handleCustomAlert(env.DB, payload);
    return json(result);
  }

  if (request.method === "POST" && url.pathname === "/api/memos/sync") {
    const payload = await readJson(request);
    const result = await handleMemoSync(env.DB, payload);
    return json(result);
  }

  if (request.method === "GET" && url.pathname === "/api/memos") {
    const ownerHandle = syncIdFromUrl(url).trim();
    if (!ownerHandle) {
      throw new BadRequest("syncId is required");
    }
    const ownerView = url.searchParams.get("ownerView") === "1";
    if (ownerView) {
      await requireReceiverAccess(env.DB, ownerHandle, accessPasswordFrom(request, url).trim());
    } else {
      await requireViewerViewAccess(env.DB, ownerHandle, viewAuthFrom(request, url));
    }
    const memos = await listSyncedMemos(env.DB, ownerHandle);
    return json({ memos });
  }

  if (request.method === "POST" && url.pathname === "/api/test-weekly-report") {
    const payload = await readJson(request);
    const result = await handleTestWeeklyReport(env.DB, payload);
    return json(result);
  }

  const readMatch = url.pathname.match(/^\/api\/messages\/(\d+)\/read$/);
  if (request.method === "POST" && readMatch) {
    const payload = await readJson(request);
    const ownerHandle = String(payload.syncId || payload.guardianHandle || "").trim().toUpperCase();
    if (!ownerHandle) {
      throw new BadRequest("syncId is required");
    }
    const viewer = await requireViewerViewAccess(env.DB, ownerHandle, viewAuthFromPayload(payload));
    await markMessageReadForViewer(env.DB, Number(readMatch[1]), ownerHandle, viewer);
    return json({ ok: true });
  }

  return json({ error: "Not found" }, 404);
}

async function startGoogleLogin(request, env) {
  requireGoogleEnv(env);
  const url = new URL(request.url);
  const state = crypto.randomUUID();
  const redirectUri = new URL("/auth/google/callback", url.origin).toString();
  const authUrl = new URL("https://accounts.google.com/o/oauth2/v2/auth");
  authUrl.searchParams.set("client_id", env.GOOGLE_CLIENT_ID);
  authUrl.searchParams.set("redirect_uri", redirectUri);
  authUrl.searchParams.set("response_type", "code");
  authUrl.searchParams.set("scope", "openid email profile");
  authUrl.searchParams.set("state", state);
  authUrl.searchParams.set("prompt", "select_account");
  return redirect(authUrl.toString(), [cookie(OAUTH_STATE_COOKIE, state, 10 * 60)]);
}

async function finishGoogleLogin(request, env) {
  requireGoogleEnv(env);
  const url = new URL(request.url);
  const expectedState = getCookie(request, OAUTH_STATE_COOKIE);
  const state = url.searchParams.get("state") || "";
  const code = url.searchParams.get("code") || "";
  if (!expectedState || expectedState !== state || !code) {
    throw new BadRequest("Invalid Google login callback");
  }

  const redirectUri = new URL("/auth/google/callback", url.origin).toString();
  const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      code,
      client_id: env.GOOGLE_CLIENT_ID,
      client_secret: env.GOOGLE_CLIENT_SECRET,
      redirect_uri: redirectUri,
      grant_type: "authorization_code",
    }),
  });
  const tokenPayload = await tokenResponse.json();
  if (!tokenResponse.ok || !tokenPayload.access_token) {
    throw new Error("Google token exchange failed");
  }

  const profileResponse = await fetch("https://openidconnect.googleapis.com/v1/userinfo", {
    headers: { authorization: `Bearer ${tokenPayload.access_token}` },
  });
  const profile = await profileResponse.json();
  if (!profileResponse.ok) {
    throw new Error("Google profile fetch failed");
  }
  if (!profile.sub || !profile.email) {
    throw new Error("Google profile is missing required fields");
  }

  const account = await upsertAccount(env.DB, profile);
  const sessionId = crypto.randomUUID();
  const expiresAt = Math.floor(Date.now() / 1000) + SESSION_TTL_SECONDS;
  await env.DB
    .prepare("INSERT INTO account_sessions(session_id, account_id, expires_at) VALUES(?1, ?2, ?3)")
    .bind(sessionId, account.id, expiresAt)
    .run();

  return redirect("/app", [
    clearCookie(OAUTH_STATE_COOKIE),
    cookie(SESSION_COOKIE, sessionId, SESSION_TTL_SECONDS),
  ]);
}

async function upsertAccount(db, profile) {
  const existing = await db
    .prepare("SELECT id, public_id FROM accounts WHERE google_sub = ?1")
    .bind(profile.sub)
    .first();
  const accountId = existing?.id || crypto.randomUUID();
  const publicId = existing?.public_id || generatePublicId();
  await db
    .prepare(
      `INSERT INTO accounts(id, google_sub, email, name, picture, public_id, updated_at)
       VALUES(?1, ?2, ?3, ?4, ?5, ?6, CURRENT_TIMESTAMP)
       ON CONFLICT(google_sub) DO UPDATE SET
         email = excluded.email,
         name = excluded.name,
         picture = excluded.picture,
         updated_at = CURRENT_TIMESTAMP`
    )
    .bind(accountId, profile.sub, profile.email, profile.name || "", profile.picture || "", publicId)
    .run();
  return db.prepare("SELECT id, email, name, picture, public_id FROM accounts WHERE google_sub = ?1").bind(profile.sub).first();
}

async function getCurrentAccount(db, request) {
  const sessionId = getCookie(request, SESSION_COOKIE);
  if (!sessionId) return null;
  const now = Math.floor(Date.now() / 1000);
  return db
    .prepare(
      `SELECT accounts.id, accounts.email, accounts.name, accounts.picture, accounts.public_id
       FROM account_sessions
       JOIN accounts ON accounts.id = account_sessions.account_id
       WHERE account_sessions.session_id = ?1 AND account_sessions.expires_at > ?2`
    )
    .bind(sessionId, now)
    .first();
}

async function requireCurrentAccount(db, request) {
  const account = await getCurrentAccount(db, request);
  if (!account) {
    throw new BadRequest("Login required");
  }
  return account;
}

function requireGoogleEnv(env) {
  if (!env.GOOGLE_CLIENT_ID || !env.GOOGLE_CLIENT_SECRET) {
    throw new BadRequest("Google login is not configured");
  }
}

function generatePublicId() {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = new Uint8Array(8);
  crypto.getRandomValues(bytes);
  let value = "SP-";
  for (let i = 0; i < bytes.length; i++) {
    if (i === 4) value += "-";
    value += alphabet[bytes[i] % alphabet.length];
  }
  return value;
}

export async function handleUnlockEvent(db, payload) {
  requireFields(payload, ["deviceId", "displayName", "guardianHandle", "localDate", "firstUnlockAt"]);

  const deviceId = String(payload.deviceId);
  const publicId = payload.publicId ? String(payload.publicId) : "";
  const displayName = String(payload.displayName);
  const guardianHandle = String(payload.guardianHandle);
  const receiverAccessKey = payload.receiverAccessKey ? String(payload.receiverAccessKey) : "";
  const localDate = String(payload.localDate);
  const firstUnlockAt = String(payload.firstUnlockAt);
  const syncSettings = readSyncSettings(payload);
  parseLocalDate(localDate);
  await requireSyncAccess(db, guardianHandle, receiverAccessKey);
  await ensureReceiverKey(db, guardianHandle, receiverAccessKey);

  await upsertUserWithSyncSettings(
    db,
    deviceId,
    publicId,
    displayName,
    guardianHandle,
    syncSettings
  );

  await db
    .prepare(
      `INSERT OR IGNORE INTO unlock_events(
         device_id, display_name, guardian_handle, local_date, first_unlock_at
       )
       VALUES(?1, ?2, ?3, ?4, ?5)`
    )
    .bind(deviceId, displayName, guardianHandle, localDate, firstUnlockAt)
    .run();

  await pruneUnlockEvents(db, deviceId, 366);

  const syncReport = await maybeCreateSyncReport(db, deviceId, displayName, guardianHandle, localDate, syncSettings);
  return {
    ok: true,
    syncReportCreated: syncReport !== null,
    weeklyReportCreated: syncReport !== null,
    syncReport,
    weeklyReport: syncReport,
    dueDate: syncReport?.dueDate || null,
    periodStart: syncReport?.periodStart || null,
    periodEnd: syncReport?.periodEnd || null,
  };
}

export async function handleInactivityAlert(db, payload) {
  requireFields(payload, ["deviceId", "displayName", "guardianHandle", "inactiveHours"]);

  const deviceId = String(payload.deviceId);
  const displayName = String(payload.displayName);
  const guardianHandle = String(payload.guardianHandle);
  const receiverAccessKey = payload.receiverAccessKey ? String(payload.receiverAccessKey) : "";
  const lastActivityAt = payload.lastActivityAt ? String(payload.lastActivityAt) : null;
  const inactiveHours = Number(payload.inactiveHours);
  if (!Number.isFinite(inactiveHours) || inactiveHours <= 0) {
    throw new BadRequest("inactiveHours must be a positive number");
  }
  await requireSyncAccess(db, guardianHandle, receiverAccessKey);
  await ensureReceiverKey(db, guardianHandle, receiverAccessKey);

  return createInactivityAlert(db, {
    deviceId,
    displayName,
    guardianHandle,
    lastActivityAt,
    inactiveHours,
  });
}

export async function runInactivityMonitor(db, nowMs) {
  const usersResult = await db
    .prepare("SELECT device_id, display_name, guardian_handle FROM users")
    .all();
  const users = usersResult.results || [];
  let checked = 0;
  let alertsCreated = 0;

  for (const user of users) {
    checked += 1;
    const lastEvent = await db
      .prepare(
        `SELECT first_unlock_at
         FROM unlock_events
         WHERE device_id = ?1
         ORDER BY first_unlock_at DESC
         LIMIT 1`
      )
      .bind(user.device_id)
      .first();
    if (!lastEvent || !lastEvent.first_unlock_at) {
      continue;
    }

    const lastMs = Date.parse(lastEvent.first_unlock_at);
    if (!Number.isFinite(lastMs)) {
      continue;
    }

    const inactiveHours = Math.floor((nowMs - lastMs) / (60 * 60 * 1000));
    if (inactiveHours < 72) {
      continue;
    }

    const result = await createInactivityAlert(db, {
      deviceId: user.device_id,
      displayName: user.display_name,
      guardianHandle: user.guardian_handle,
      lastActivityAt: lastEvent.first_unlock_at,
      inactiveHours,
    });
    if (!result.duplicate) {
      alertsCreated += 1;
    }
  }

  return { ok: true, checked, alertsCreated };
}

async function createInactivityAlert(db, alert) {
  const existing = await db
    .prepare("SELECT id FROM inactivity_alerts WHERE device_id = ?1 AND last_activity_at IS ?2")
    .bind(alert.deviceId, alert.lastActivityAt)
    .first();
  if (existing) {
    return { ok: true, duplicate: true };
  }

  const title = "长时间未同步提醒";
  const body =
    `${alert.displayName} 已经超过 ${alert.inactiveHours} 小时没有新的首次解锁记录。\n\n` +
    "可能是手机关机、没网、App 被卸载、省电策略限制，或确实长时间没有使用手机。建议你直接联系确认情况。";
  const messageId = await createMessage(db, {
    recipientHandle: alert.guardianHandle,
    senderDeviceId: alert.deviceId,
    senderDisplayName: alert.displayName,
    type: "inactivity_alert",
    title,
    body,
  });

  await db
    .prepare(
      `INSERT INTO inactivity_alerts(
         device_id, guardian_handle, last_activity_at, inactive_hours, message_id
       )
       VALUES(?1, ?2, ?3, ?4, ?5)`
    )
    .bind(alert.deviceId, alert.guardianHandle, alert.lastActivityAt, alert.inactiveHours, messageId)
    .run();

  return { ok: true, messageId };
}

export async function handleTestWeeklyReport(db, payload) {
  requireFields(payload, ["deviceId", "displayName", "guardianHandle"]);

  const deviceId = String(payload.deviceId);
  const displayName = String(payload.displayName);
  const guardianHandle = String(payload.guardianHandle);
  const receiverAccessKey = payload.receiverAccessKey ? String(payload.receiverAccessKey) : "";
  await requireSyncAccess(db, guardianHandle, receiverAccessKey);
  await ensureReceiverKey(db, guardianHandle, receiverAccessKey);
  const eventsResult = await db
    .prepare(
      `SELECT local_date, first_unlock_at
       FROM unlock_events
       WHERE device_id = ?1
       ORDER BY local_date DESC
       LIMIT 7`
    )
    .bind(deviceId)
    .all();
  const events = (eventsResult.results || []).reverse();
  if (events.length === 0) {
    throw new BadRequest("No unlock records found for this device. Record one unlock first.");
  }

  const title = `${displayName} 的测试状态周报`;
  const body = [
    `${displayName} 的测试状态记录`,
    "",
    ...events.map((event) => `${event.local_date}  ${event.first_unlock_at}`),
    "",
    "这是一条测试消息，用来确认状态页可以收到周报。",
  ].join("\n");
  const messageId = await createMessage(db, {
    recipientHandle: guardianHandle,
    senderDeviceId: deviceId,
    senderDisplayName: displayName,
    type: "weekly_report",
    title,
    body,
  });
  await deleteOldWeeklyReportMessages(db, guardianHandle, deviceId, messageId);
  return { ok: true, messageId };
}

/** 自动化规则触发的自定义消息，直接进入查看人的消息列表。 */
export async function handleCustomAlert(db, payload) {
  requireFields(payload, ["deviceId", "displayName", "guardianHandle", "text"]);

  const deviceId = String(payload.deviceId);
  const displayName = String(payload.displayName);
  const guardianHandle = String(payload.guardianHandle);
  const receiverAccessKey = payload.receiverAccessKey ? String(payload.receiverAccessKey) : "";
  const text = String(payload.text).slice(0, 1000);
  await requireSyncAccess(db, guardianHandle, receiverAccessKey);
  await ensureReceiverKey(db, guardianHandle, receiverAccessKey);

  const messageId = await createMessage(db, {
    recipientHandle: guardianHandle,
    senderDeviceId: deviceId,
    senderDisplayName: displayName,
    type: "custom_alert",
    title: `${displayName} 的自动提醒`,
    body: text,
  });
  return { ok: true, messageId };
}

/**
 * 全量同步：App 每次上传全部非私密备忘，服务端以 device_id 维度整体替换。
 */
export async function handleMemoSync(db, payload) {
  requireFields(payload, ["deviceId", "guardianHandle"]);

  const deviceId = String(payload.deviceId);
  const guardianHandle = String(payload.guardianHandle);
  const receiverAccessKey = payload.receiverAccessKey ? String(payload.receiverAccessKey) : "";
  await requireSyncAccess(db, guardianHandle, receiverAccessKey);
  await ensureReceiverKey(db, guardianHandle, receiverAccessKey);

  const memos = Array.isArray(payload.memos) ? payload.memos : [];
  if (memos.length > 500) {
    throw new BadRequest("Too many memos in one sync (max 500)");
  }

  await db.prepare("DELETE FROM memos WHERE device_id = ?1").bind(deviceId).run();
  let synced = 0;
  for (const memo of memos) {
    const clientId = Number(memo.clientId);
    if (!Number.isFinite(clientId) || clientId <= 0) {
      continue;
    }
    await db
      .prepare(
        `INSERT INTO memos(device_id, guardian_handle, client_id, title, content, type, memo_date, pinned, done, updated_at)
         VALUES(?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)`
      )
      .bind(
        deviceId,
        guardianHandle,
        clientId,
        String(memo.title || "").slice(0, 200),
        String(memo.content || "").slice(0, 4000),
        memo.type === "checklist" ? "checklist" : "text",
        String(memo.memoDate || "").slice(0, 10),
        memo.pinned ? 1 : 0,
        memo.done ? 1 : 0,
        Number(memo.updatedAt) || 0
      )
      .run();
    synced += 1;
  }
  return { ok: true, synced };
}

export async function listSyncedMemos(db, guardianHandle) {
  const result = await db
    .prepare(
      `SELECT client_id, title, content, type, memo_date, pinned, done, updated_at
       FROM memos
       WHERE guardian_handle = ?1
       ORDER BY pinned DESC, done ASC, updated_at DESC
       LIMIT 200`
    )
    .bind(guardianHandle)
    .all();
  return result.results || [];
}

export async function maybeCreateWeeklyReport(db, deviceId, displayName, guardianHandle, localDate) {
  const settings = await loadUserSyncSettings(db, deviceId);
  return maybeCreateSyncReport(db, deviceId, displayName, guardianHandle, localDate, settings);
}

export async function maybeCreateSyncReport(db, deviceId, displayName, guardianHandle, localDate, syncSettings) {
  const settings = syncSettings || (await loadUserSyncSettings(db, deviceId));
  if (!isSyncDay(localDate, settings)) {
    return null;
  }

  const dueDate = localDate;
  const period = computeSyncPeriod(dueDate, settings);
  const existing = await db
    .prepare("SELECT id FROM weekly_reports WHERE device_id = ?1 AND week_start = ?2")
    .bind(deviceId, dueDate)
    .first();
  if (existing) {
    return null;
  }

  let events = await listUnlockEventsBetween(db, deviceId, period.periodStart, period.periodEnd);
  const totalCount = await countUnlockEvents(db, deviceId);
  const expectedDays = period.expectedDays;

  if (settings.syncMode === "weekday") {
    if (totalCount < expectedDays) {
      events = await listAllUnlockEvents(db, deviceId);
    } else if (events.length < expectedDays && events.length > 0) {
      events = await listUnlockEventsBetween(db, deviceId, events[0].local_date, period.periodEnd);
    }
  } else if (events.length < expectedDays) {
    if (events.length > 0) {
      events = await listUnlockEventsBetween(db, deviceId, events[0].local_date, period.periodEnd);
    } else {
      events = await listUnlockEventsBetween(db, deviceId, period.periodStart, period.periodEnd);
    }
  }

  if (events.length === 0) {
    return null;
  }

  const actualStart = events[0].local_date;
  const actualEnd = events[events.length - 1].local_date;
  const title = `${displayName} 的状态记录`;
  const body = formatSyncReportBody(displayName, actualStart, actualEnd, events);
  const messageId = await createMessage(db, {
    recipientHandle: guardianHandle,
    senderDeviceId: deviceId,
    senderDisplayName: displayName,
    type: "weekly_report",
    title,
    body,
  });
  await deleteOldWeeklyReportMessages(db, guardianHandle, deviceId, messageId);

  await db
    .prepare(
      `INSERT INTO weekly_reports(device_id, guardian_handle, week_start, week_end, message_id)
       VALUES(?1, ?2, ?3, ?4, ?5)`
    )
    .bind(deviceId, guardianHandle, dueDate, actualEnd, messageId)
    .run();

  return {
    messageId,
    dueDate,
    periodStart: actualStart,
    periodEnd: actualEnd,
    weekStart: actualStart,
    weekEnd: actualEnd,
    eventCount: events.length,
  };
}

export async function migrateDeviceForPublicId(db, deviceId, publicId, guardianHandle) {
  const lookupId = String(publicId || guardianHandle || "").trim().toUpperCase();
  if (!lookupId) {
    return false;
  }

  const existing = await db
    .prepare(
      `SELECT device_id
       FROM users
       WHERE device_id <> ?1
         AND (
           UPPER(COALESCE(public_id, '')) = ?2
           OR UPPER(guardian_handle) = ?2
         )
       LIMIT 1`
    )
    .bind(deviceId, lookupId)
    .first();
  if (!existing) {
    return false;
  }

  await reassignDeviceRecords(db, existing.device_id, deviceId);
  return true;
}

async function reassignDeviceRecords(db, oldDeviceId, newDeviceId) {
  const statements = [
    "UPDATE unlock_events SET device_id = ?1 WHERE device_id = ?2",
    "UPDATE weekly_reports SET device_id = ?1 WHERE device_id = ?2",
    "UPDATE inactivity_alerts SET device_id = ?1 WHERE device_id = ?2",
    "UPDATE messages SET sender_device_id = ?1 WHERE sender_device_id = ?2",
    "UPDATE memos SET device_id = ?1 WHERE device_id = ?2",
  ];
  for (const sql of statements) {
    await db.prepare(sql).bind(newDeviceId, oldDeviceId).run();
  }
  await db.prepare("DELETE FROM users WHERE device_id = ?1").bind(oldDeviceId).run();
}

export async function upsertUserWithSyncSettings(
  db,
  deviceId,
  publicId,
  displayName,
  guardianHandle,
  syncSettings
) {
  await migrateDeviceForPublicId(db, deviceId, publicId, guardianHandle);

  try {
    await upsertUserRow(db, deviceId, publicId, displayName, guardianHandle, syncSettings, true);
  } catch (error) {
    const message = String(error?.message || error);
    if (message.includes("public_id") || message.includes("UNIQUE constraint")) {
      await migrateDeviceForPublicId(db, deviceId, publicId, guardianHandle);
      await upsertUserRow(db, deviceId, publicId, displayName, guardianHandle, syncSettings, true);
      return;
    }
    if (!message.includes("sync_mode") && !message.includes("no such column")) {
      throw error;
    }
    await upsertUserRow(db, deviceId, publicId, displayName, guardianHandle, syncSettings, false);
  }
}

async function upsertUserRow(
  db,
  deviceId,
  publicId,
  displayName,
  guardianHandle,
  syncSettings,
  includeSyncSettings
) {
  if (includeSyncSettings) {
    await db
      .prepare(
        `INSERT INTO users(device_id, public_id, display_name, guardian_handle, sync_mode, sync_weekdays_mask, sync_anchor_date, sync_interval_days, updated_at)
         VALUES(?1, NULLIF(?2, ''), ?3, ?4, ?5, ?6, ?7, ?8, CURRENT_TIMESTAMP)
         ON CONFLICT(device_id) DO UPDATE SET
           public_id = COALESCE(excluded.public_id, users.public_id),
           display_name = excluded.display_name,
           guardian_handle = excluded.guardian_handle,
           sync_mode = excluded.sync_mode,
           sync_weekdays_mask = excluded.sync_weekdays_mask,
           sync_anchor_date = excluded.sync_anchor_date,
           sync_interval_days = excluded.sync_interval_days,
           updated_at = CURRENT_TIMESTAMP`
      )
      .bind(
        deviceId,
        publicId,
        displayName,
        guardianHandle,
        syncSettings.syncMode,
        syncSettings.syncWeekdaysMask,
        syncSettings.syncAnchorDate,
        syncSettings.syncIntervalDays
      )
      .run();
    return;
  }

  await db
    .prepare(
      `INSERT INTO users(device_id, public_id, display_name, guardian_handle, updated_at)
       VALUES(?1, NULLIF(?2, ''), ?3, ?4, CURRENT_TIMESTAMP)
       ON CONFLICT(device_id) DO UPDATE SET
         public_id = COALESCE(excluded.public_id, users.public_id),
         display_name = excluded.display_name,
         guardian_handle = excluded.guardian_handle,
         updated_at = CURRENT_TIMESTAMP`
    )
    .bind(deviceId, publicId, displayName, guardianHandle)
    .run();
}

export function readSyncSettings(payload) {
  const syncMode = String(payload.syncMode || "weekday") === "interval" ? "interval" : "weekday";
  const syncWeekdaysMask = Number(payload.syncWeekdaysMask || 0) || DEFAULT_WEEKDAY_MASK;
  const syncAnchorDate = String(payload.syncAnchorDate || payload.localDate || "");
  const syncIntervalDays = clampIntervalDays(Number(payload.syncIntervalDays || 7));
  return {
    syncMode,
    syncWeekdaysMask,
    syncAnchorDate,
    syncIntervalDays,
  };
}

export async function loadUserSyncSettings(db, deviceId) {
  const user = await db
    .prepare(
      "SELECT sync_mode, sync_weekdays_mask, sync_anchor_date, sync_interval_days FROM users WHERE device_id = ?1"
    )
    .bind(deviceId)
    .first();
  if (!user) {
    return {
      syncMode: "weekday",
      syncWeekdaysMask: DEFAULT_WEEKDAY_MASK,
      syncAnchorDate: "",
      syncIntervalDays: 7,
    };
  }
  return {
    syncMode: user.sync_mode === "interval" ? "interval" : "weekday",
    syncWeekdaysMask: Number(user.sync_weekdays_mask || DEFAULT_WEEKDAY_MASK),
    syncAnchorDate: String(user.sync_anchor_date || ""),
    syncIntervalDays: clampIntervalDays(Number(user.sync_interval_days || 7)),
  };
}

export function isSyncDay(localDate, settings) {
  const date = parseLocalDate(localDate);
  if (settings.syncMode === "weekday") {
    const day = date.getUTCDay();
    const bitIndex = day === 0 ? 6 : day - 1;
    const mask = Number(settings.syncWeekdaysMask || DEFAULT_WEEKDAY_MASK);
    return (mask & (1 << bitIndex)) !== 0;
  }
  const anchor = settings.syncAnchorDate ? parseLocalDate(settings.syncAnchorDate) : date;
  const due = latestDueDateOnOrBefore(date, anchor, settings.syncIntervalDays);
  return due !== null && formatDate(due) === localDate;
}

export function computeSyncPeriod(dueDate, settings) {
  const due = parseLocalDate(dueDate);
  if (settings.syncMode === "weekday") {
    return {
      periodStart: formatDate(addDays(due, -(WEEKDAY_PERIOD_DAYS - 1))),
      periodEnd: dueDate,
      expectedDays: WEEKDAY_PERIOD_DAYS,
    };
  }
  const intervalDays = clampIntervalDays(Number(settings.syncIntervalDays || 7));
  return {
    periodStart: formatDate(addDays(due, -intervalDays)),
    periodEnd: dueDate,
    expectedDays: intervalDays,
  };
}

export function latestDueDateOnOrBefore(targetDate, anchorDate, intervalDays) {
  const interval = clampIntervalDays(Number(intervalDays || 7));
  const target = parseLocalDate(formatDate(targetDate));
  const anchor = parseLocalDate(formatDate(anchorDate));
  if (target.getTime() < anchor.getTime()) {
    return null;
  }
  const daysBetween = Math.floor((target.getTime() - anchor.getTime()) / (24 * 60 * 60 * 1000));
  const periods = Math.floor(daysBetween / interval);
  return addDays(anchor, periods * interval);
}

export function clampIntervalDays(value) {
  if (!Number.isFinite(value) || value <= 0) {
    return 7;
  }
  return Math.max(1, Math.min(180, Math.floor(value)));
}

const WEEKDAY_PERIOD_DAYS = 7;
const DEFAULT_WEEKDAY_MASK = 1;

export async function pruneUnlockEvents(db, deviceId, maxRecords) {
  const countRow = await db
    .prepare("SELECT COUNT(*) AS total FROM unlock_events WHERE device_id = ?1")
    .bind(deviceId)
    .first();
  const total = Number(countRow?.total || 0);
  if (total <= maxRecords) {
    return;
  }
  const toDelete = total - maxRecords;
  await db
    .prepare(
      `DELETE FROM unlock_events
       WHERE id IN (
         SELECT id FROM unlock_events
         WHERE device_id = ?1
         ORDER BY local_date ASC
         LIMIT ?2
       )`
    )
    .bind(deviceId, toDelete)
    .run();
}

async function countUnlockEvents(db, deviceId) {
  const row = await db
    .prepare("SELECT COUNT(*) AS total FROM unlock_events WHERE device_id = ?1")
    .bind(deviceId)
    .first();
  return Number(row?.total || 0);
}

async function listUnlockEventsBetween(db, deviceId, startDate, endDate) {
  const eventsResult = await db
    .prepare(
      `SELECT local_date, first_unlock_at
       FROM unlock_events
       WHERE device_id = ?1 AND local_date BETWEEN ?2 AND ?3
       ORDER BY local_date ASC`
    )
    .bind(deviceId, startDate, endDate)
    .all();
  return eventsResult.results || [];
}

async function listAllUnlockEvents(db, deviceId) {
  const eventsResult = await db
    .prepare(
      `SELECT local_date, first_unlock_at
       FROM unlock_events
       WHERE device_id = ?1
       ORDER BY local_date ASC`
    )
    .bind(deviceId)
    .all();
  return eventsResult.results || [];
}

export function formatSyncReportBody(displayName, periodStart, periodEnd, events) {
  const lines = [
    `${displayName} 的状态记录`,
    `${periodStart} 至 ${periodEnd}`,
    "",
  ];
  for (const event of events) {
    lines.push(`${event.local_date}  ${event.first_unlock_at}`);
  }
  lines.push("", "这表示记录端在这些日期有首次解锁活动。若记录缺失，可能是未解锁、关机、没网或系统限制。");
  return lines.join("\n");
}

export function eligibleReportWeek(anchor) {
  const day = anchor.getUTCDay();
  const mondayIndex = day === 0 ? 6 : day - 1;
  if (day === 0) {
    return {
      weekStart: addDays(anchor, -6),
      weekEnd: anchor,
    };
  }
  const previousWeekEnd = addDays(anchor, -(mondayIndex + 1));
  return {
    weekStart: addDays(previousWeekEnd, -6),
    weekEnd: previousWeekEnd,
  };
}

export function formatWeeklyReportBody(displayName, weekStart, weekEnd, events) {
  const lines = [
    `${displayName} 的状态记录`,
    `${formatDate(weekStart)} 至 ${formatDate(weekEnd)}`,
    "",
  ];
  for (const event of events) {
    lines.push(`${event.local_date}  ${event.first_unlock_at}`);
  }
  lines.push("", "这表示记录端在这些日期有首次解锁活动。若记录缺失，可能是未解锁、关机、没网或系统限制。");
  return lines.join("\n");
}

export async function createMessage(db, message) {
  const result = await db
    .prepare(
      `INSERT INTO messages(
         recipient_handle, sender_device_id, sender_display_name, type, title, body
       )
       VALUES(?1, ?2, ?3, ?4, ?5, ?6)`
    )
    .bind(
      message.recipientHandle,
      message.senderDeviceId,
      message.senderDisplayName,
      message.type,
      message.title,
      message.body
    )
    .run();
  return result.meta.last_row_id;
}

export async function deleteOldWeeklyReportMessages(db, recipientHandle, senderDeviceId, keepMessageId) {
  await db
    .prepare(
      `DELETE FROM message_reads
       WHERE message_id IN (
         SELECT id FROM messages
         WHERE recipient_handle = ?1
           AND sender_device_id = ?2
           AND type = 'weekly_report'
           AND id <> ?3
       )`
    )
    .bind(recipientHandle, senderDeviceId, keepMessageId)
    .run();
  await db
    .prepare(
      `DELETE FROM messages
       WHERE recipient_handle = ?1
         AND sender_device_id = ?2
         AND type = 'weekly_report'
         AND id <> ?3`
    )
    .bind(recipientHandle, senderDeviceId, keepMessageId)
    .run();
}

export function keepLatestWeeklyReport(messages) {
  const seenSenders = new Set();
  return messages.filter((message) => {
    if (message.type !== "weekly_report") {
      return true;
    }
    const key = String(message.sender_device_id ?? "");
    if (seenSenders.has(key)) {
      return false;
    }
    seenSenders.add(key);
    return true;
  });
}

export async function listMessages(db, guardianHandle) {
  return listMessagesForOwner(db, guardianHandle);
}

export async function listMessagesForViewer(db, ownerHandle, viewerPublicId) {
  const result = await db
    .prepare(
      `SELECT m.id, m.sender_device_id, m.sender_display_name, m.type, m.title, m.body, m.created_at,
              mr.read_at AS read_at
       FROM messages m
       LEFT JOIN message_reads mr
         ON mr.message_id = m.id
        AND mr.viewer_public_id = ?2
        AND mr.owner_public_id = ?1
       WHERE m.recipient_handle = ?1
       ORDER BY m.created_at DESC, m.id DESC
       LIMIT 100`
    )
    .bind(ownerHandle, viewerPublicId)
    .all();
  return keepLatestWeeklyReport(result.results || []);
}

export async function listMessagesForOwner(db, ownerHandle) {
  const messages = await db
    .prepare(
      `SELECT id, sender_device_id, sender_display_name, type, title, body, created_at
       FROM messages
       WHERE recipient_handle = ?1
       ORDER BY created_at DESC, id DESC
       LIMIT 100`
    )
    .bind(ownerHandle)
    .all();
  const allowlist = await listOwnerViewers(db, ownerHandle);
  const reads = await db
    .prepare(
      `SELECT message_id, viewer_public_id, viewer_nickname, read_at
       FROM message_reads
       WHERE owner_public_id = ?1`
    )
    .bind(ownerHandle)
    .all();
  const readRows = reads.results || [];
  return keepLatestWeeklyReport(messages.results || []).map((message) => ({
    ...message,
    readers: buildReaderStatuses(allowlist, readRows.filter((row) => row.message_id === message.id)),
  }));
}

function buildReaderStatuses(allowlist, readRows) {
  const readByNickname = new Map();
  for (const row of readRows) {
    readByNickname.set(normalizeNickname(row.viewer_nickname), row.read_at);
  }
  return allowlist.map((viewer) => {
    const readAt = readByNickname.get(normalizeNickname(viewer.viewer_nickname)) || null;
    return {
      id: viewer.id,
      nickname: viewer.viewer_nickname,
      read: Boolean(readAt),
      read_at: readAt,
    };
  });
}

export async function listUnlockEvents(db, guardianHandle) {
  const result = await db
    .prepare(
      `SELECT device_id, display_name, local_date, first_unlock_at, created_at
       FROM unlock_events
       WHERE guardian_handle = ?1
       ORDER BY local_date DESC, first_unlock_at DESC
       LIMIT 30`
    )
    .bind(guardianHandle)
    .all();
  return result.results || [];
}

/**
 * 把解锁事件按「同步周期」归类，返回最近 limit 段（最新在前）。
 * - interval 模式用 syncIntervalDays，weekday 模式按 7 天一段；锚点用 syncAnchorDate，
 *   无锚点则用最早一条记录对齐。
 * - 只返回「已过完」的时段（periodEnd < todayStr），避免把还没走完的周期当完整数据。
 * - 每段附 days: [{date, has}]，标出该周期内每一天是否有解锁记录（用于前端标注缺失天）。
 */
export function computeStatusPeriods(dates, settings, todayStr, limit = 5) {
  const valid = (dates || []).filter(Boolean).slice().sort();
  if (!valid.length) {
    return [];
  }
  const interval = settings.syncMode === "interval"
    ? clampIntervalDays(Number(settings.syncIntervalDays || 7))
    : WEEKDAY_PERIOD_DAYS;
  const anchor = parseLocalDate(settings.syncAnchorDate || valid[0]);
  const dayMs = 24 * 60 * 60 * 1000;
  const present = new Set(valid);
  const indices = new Set();
  for (const d of valid) {
    const dt = parseLocalDate(d);
    indices.add(Math.floor((dt.getTime() - anchor.getTime()) / dayMs / interval));
  }
  const result = [];
  for (const idx of [...indices].sort((a, b) => b - a)) {
    const start = addDays(anchor, idx * interval);
    const end = addDays(anchor, (idx + 1) * interval - 1);
    const endStr = formatDate(end);
    // 未过完的周期跳过（结束日还没到就不显示，防止半截数据）。
    if (todayStr && endStr >= todayStr) {
      continue;
    }
    const days = [];
    for (let i = 0; i < interval; i++) {
      const dateStr = formatDate(addDays(start, i));
      days.push({ date: dateStr, has: present.has(dateStr) });
    }
    result.push({ periodStart: formatDate(start), periodEnd: endStr, days });
    if (result.length >= limit) {
      break;
    }
  }
  return result;
}

export async function getReceiverSummary(db, guardianHandle, nowMs) {
  const lastEvent = await db
    .prepare(
      `SELECT device_id, display_name, local_date, first_unlock_at, created_at
       FROM unlock_events
       WHERE guardian_handle = ?1
       ORDER BY first_unlock_at DESC
       LIMIT 1`
    )
    .bind(guardianHandle)
    .first();
  const messageStats = await db
    .prepare("SELECT COUNT(*) AS total_messages FROM messages WHERE recipient_handle = ?1")
    .bind(guardianHandle)
    .first();

  let inactiveHours = null;
  let status = "no_data";
  if (lastEvent && lastEvent.first_unlock_at) {
    const lastMs = Date.parse(lastEvent.first_unlock_at);
    if (Number.isFinite(lastMs)) {
      inactiveHours = Math.max(0, Math.floor((nowMs - lastMs) / (60 * 60 * 1000)));
      status = inactiveHours >= 72 ? "inactive_alert" : "active";
    }
  }

  const ownerAccount = await db
    .prepare("SELECT nickname FROM local_accounts WHERE public_id = ?1")
    .bind(guardianHandle)
    .first();

  let statusPeriods = [];
  try {
    if (lastEvent && lastEvent.device_id) {
      const settings = await loadUserSyncSettings(db, lastEvent.device_id);
      const dateRows = await db
        .prepare(
          `SELECT local_date FROM unlock_events WHERE guardian_handle = ?1 ORDER BY local_date ASC`
        )
        .bind(guardianHandle)
        .all();
      const dates = (dateRows.results || []).map((row) => row.local_date);
      statusPeriods = computeStatusPeriods(dates, settings, formatDate(new Date(nowMs)));
    }
  } catch (error) {
    statusPeriods = [];
  }

  return {
    status,
    inactiveHours,
    lastActivity: lastEvent || null,
    ownerNickname: ownerAccount?.nickname || null,
    totalMessages: Number(messageStats?.total_messages || 0),
    unreadMessages: 0,
    statusPeriods,
  };
}

export async function getViewerSummary(db, ownerHandle, viewerPublicId, nowMs) {
  const base = await getReceiverSummary(db, ownerHandle, nowMs);
  const unread = await db
    .prepare(
      `SELECT COUNT(*) AS unread_messages
       FROM messages m
       LEFT JOIN message_reads mr
         ON mr.message_id = m.id
        AND mr.viewer_public_id = ?2
        AND mr.owner_public_id = ?1
       WHERE m.recipient_handle = ?1
         AND mr.read_at IS NULL`
    )
    .bind(ownerHandle, viewerPublicId)
    .first();
  return {
    ...base,
    unreadMessages: Number(unread?.unread_messages || 0),
    viewerUnreadMessages: Number(unread?.unread_messages || 0),
  };
}

export async function markMessageRead(db, messageId, guardianHandle = "", viewer = null) {
  if (viewer) {
    await markMessageReadForViewer(db, messageId, guardianHandle, viewer);
    return;
  }
  await db
    .prepare("UPDATE messages SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP) WHERE id = ?1")
    .bind(messageId)
    .run();
}

export async function markMessageReadForViewer(db, messageId, ownerHandle, viewer) {
  const message = await db
    .prepare("SELECT id FROM messages WHERE id = ?1 AND recipient_handle = ?2")
    .bind(messageId, ownerHandle)
    .first();
  if (!message) {
    throw new BadRequest("Message not found");
  }
  await db
    .prepare(
      `INSERT INTO message_reads(message_id, owner_public_id, viewer_public_id, viewer_nickname, read_at)
       VALUES(?1, ?2, ?3, ?4, CURRENT_TIMESTAMP)
       ON CONFLICT(message_id, viewer_public_id) DO UPDATE SET
         read_at = COALESCE(message_reads.read_at, CURRENT_TIMESTAMP),
         viewer_nickname = excluded.viewer_nickname`
    )
    .bind(messageId, ownerHandle, viewer.publicId, viewer.nickname)
    .run();
}

export async function listOwnerViewers(db, ownerPublicId) {
  const result = await db
    .prepare(
      `SELECT ov.id, ov.viewer_nickname, ov.created_at, ov.updated_at,
              la.public_id AS viewer_public_id
       FROM owner_viewers ov
       LEFT JOIN local_accounts la
         ON lower(la.nickname) = lower(ov.viewer_nickname)
        AND la.account_role = 'viewer'
       WHERE ov.owner_public_id = ?1
       ORDER BY ov.viewer_nickname COLLATE NOCASE ASC, ov.id ASC`
    )
    .bind(ownerPublicId)
    .all();
  return result.results || [];
}

export async function addOwnerViewer(db, ownerPublicId, nickname) {
  const viewerNickname = normalizeNickname(String(nickname || ""));
  if (!viewerNickname) {
    throw new BadRequest("Nickname is required");
  }
  try {
    const result = await db
      .prepare(
        `INSERT INTO owner_viewers(owner_public_id, viewer_nickname, updated_at)
         VALUES(?1, ?2, CURRENT_TIMESTAMP)`
      )
      .bind(ownerPublicId, viewerNickname)
      .run();
    return {
      id: result.meta.last_row_id,
      viewer_nickname: viewerNickname,
    };
  } catch (error) {
    throw new BadRequest("该昵称已在查看名单中");
  }
}

export async function updateOwnerViewer(db, ownerPublicId, viewerId, nickname) {
  const viewerNickname = normalizeNickname(String(nickname || ""));
  if (!viewerNickname) {
    throw new BadRequest("Nickname is required");
  }
  const result = await db
    .prepare(
      `UPDATE owner_viewers
       SET viewer_nickname = ?3, updated_at = CURRENT_TIMESTAMP
       WHERE id = ?1 AND owner_public_id = ?2`
    )
    .bind(viewerId, ownerPublicId, viewerNickname)
    .run();
  if (!result.meta.changes) {
    throw new BadRequest("查看人不存在");
  }
  return { id: viewerId, viewer_nickname: viewerNickname };
}

export async function deleteOwnerViewer(db, ownerPublicId, viewerId) {
  const result = await db
    .prepare("DELETE FROM owner_viewers WHERE id = ?1 AND owner_public_id = ?2")
    .bind(viewerId, ownerPublicId)
    .run();
  if (!result.meta.changes) {
    throw new BadRequest("查看人不存在");
  }
}

export async function ensureReceiverKey(db, guardianHandle, accessKey) {
  if (!accessKey) {
    return;
  }
  const existing = await db
    .prepare("SELECT access_key, access_key_hash, access_key_salt FROM receiver_keys WHERE guardian_handle = ?1")
    .bind(guardianHandle)
    .first();
  if (existing) {
    if (!(await verifyReceiverKeyRow(db, guardianHandle, existing, accessKey))) {
      throw new BadRequest("Receiver access key does not match this receiver ID");
    }
    return;
  }
  const { salt, hash } = await makeReceiverKeyHash(accessKey);
  await db
    .prepare(
      `INSERT INTO receiver_keys(guardian_handle, access_key, access_key_hash, access_key_salt, updated_at)
       VALUES(?1, '', ?2, ?3, CURRENT_TIMESTAMP)`
    )
    .bind(guardianHandle, hash, salt)
    .run();
}

export async function requireReceiverAccess(db, guardianHandle, accessKey) {
  const localAccount = await db
    .prepare("SELECT password_salt, password_hash FROM local_accounts WHERE public_id = ?1")
    .bind(guardianHandle)
    .first();
  if (localAccount) {
    if (!accessKey || !(await verifyPassword(accessKey, localAccount.password_salt, localAccount.password_hash))) {
      throw new BadRequest("UID or password is incorrect");
    }
    return;
  }

  const existing = await db
    .prepare("SELECT access_key, access_key_hash, access_key_salt FROM receiver_keys WHERE guardian_handle = ?1")
    .bind(guardianHandle)
    .first();
  // 默认拒绝：既没有网页账号、也没有 receiver key 的 UID 不允许被查看
  // （防止仅凭猜到 UID 就读取签到/备忘）。
  if (!existing) {
    throw new BadRequest("此 UID 尚未设置访问密钥，无法查看");
  }
  if (!(await verifyReceiverKeyRow(db, guardianHandle, existing, accessKey))) {
    throw new BadRequest("Receiver access key is required");
  }
}

export async function requireSyncAccess(db, guardianHandle, accessKey) {
  const localAccount = await db
    .prepare("SELECT password_salt, password_hash FROM local_accounts WHERE public_id = ?1")
    .bind(guardianHandle)
    .first();
  if (!localAccount) {
    return;
  }
  if (!accessKey || !(await verifyPassword(accessKey, localAccount.password_salt, localAccount.password_hash))) {
    throw new BadRequest("UID or password is incorrect");
  }
}

export async function registerLocalAccount(db, payload) {
  const nickname = String(payload.nickname || "").trim();
  const password = String(payload.password || "");
  if (!nickname) {
    throw new BadRequest("Nickname is required");
  }
  if (password.length < 8) {
    throw new BadRequest("Password must be at least 8 characters");
  }

  for (let attempt = 0; attempt < 5; attempt++) {
    const publicId = generatePublicId();
    const salt = randomBase64(16);
    const hash = await hashPassword(password, salt);
    try {
      const accountRole = payload.role === "viewer" ? "viewer" : "owner";
      await db
        .prepare(
          `INSERT INTO local_accounts(public_id, nickname, password_salt, password_hash, account_role)
           VALUES(?1, ?2, ?3, ?4, ?5)`
        )
        .bind(publicId, nickname, salt, hash, accountRole)
        .run();
      return { publicId, nickname, role: accountRole };
    } catch (error) {
      if (attempt === 4) throw error;
    }
  }
  throw new Error("Unable to create account");
}

export async function loginLocalAccount(db, payload) {
  const password = String(payload.password || "");
  if (!password) {
    throw new BadRequest("Password is required");
  }

  const publicId = String(payload.publicId || payload.uid || "").trim().toUpperCase();
  const nickname = normalizeNickname(payload.nickname || "");
  let account = null;
  if (publicId) {
    account = await getLocalAccount(db, publicId);
    if (account && !(await verifyPassword(password, account.password_salt, account.password_hash))) {
      account = null;
    }
  } else if (nickname) {
    account = await findLocalAccountForNicknameLogin(db, nickname, password);
  } else {
    throw new BadRequest("UID is required");
  }

  if (!account) {
    throw new BadRequest(publicId ? "UID 或密码不正确" : "昵称或密码不正确");
  }
  return { publicId: account.public_id, nickname: account.nickname, role: account.account_role || "owner" };
}

export async function recoverViewerUid(db, payload) {
  const viewerNickname = normalizeNickname(payload.viewerNickname || payload.nickname || "");
  const ownerNickname = normalizeNickname(payload.ownerNickname || payload.childNickname || "");
  const password = String(payload.password || "");
  if (!viewerNickname) {
    throw new BadRequest("请填写您的昵称");
  }
  if (!ownerNickname) {
    throw new BadRequest("请填写孩子的昵称");
  }
  if (!password) {
    throw new BadRequest("请填写密码");
  }

  const viewer = await findLocalAccountForNicknameLogin(db, viewerNickname, password);
  if (!viewer) {
    throw new BadRequest("昵称或密码不正确");
  }
  if ((viewer.account_role || "owner") !== "viewer") {
    throw new BadRequest("请使用网页注册的查看账号");
  }

  const owner = await findOwnerAccountByNickname(db, ownerNickname);
  if (!owner) {
    throw new BadRequest("未找到对应的孩子账号，请确认孩子昵称");
  }
  if (!(await isViewerAllowed(db, owner.public_id, viewer.nickname))) {
    throw new BadRequest("您的昵称未在对方的查看名单中，请联系孩子在手机端添加");
  }

  return {
    publicId: viewer.public_id,
    nickname: viewer.nickname,
    ownerNickname: owner.nickname,
    role: viewer.account_role || "viewer",
  };
}

async function findOwnerAccountByNickname(db, nickname) {
  return db
    .prepare(
      `SELECT public_id, nickname, account_role
       FROM local_accounts
       WHERE lower(nickname) = lower(?1)
         AND account_role = 'owner'`
    )
    .bind(nickname)
    .first();
}

async function findLocalAccountForNicknameLogin(db, nickname, password) {
  const result = await db
    .prepare(
      `SELECT public_id, nickname, password_salt, password_hash, account_role
       FROM local_accounts
       WHERE lower(nickname) = lower(?1)`
    )
    .bind(nickname)
    .all();
  const accounts = result.results || [];
  if (!accounts.length) {
    return null;
  }
  const matches = [];
  for (const account of accounts) {
    if (await verifyPassword(password, account.password_salt, account.password_hash)) {
      matches.push(account);
    }
  }
  if (matches.length === 1) {
    return matches[0];
  }
  if (matches.length > 1) {
    throw new BadRequest("该昵称对应多个账号，请联系对方确认");
  }
  return null;
}

export async function changeLocalAccountPassword(db, payload) {
  const publicId = String(payload.publicId || payload.uid || "").trim().toUpperCase();
  const currentPassword = String(payload.currentPassword || payload.password || "");
  const newPassword = String(payload.newPassword || "");
  if (!publicId) {
    throw new BadRequest("UID is required");
  }
  if (!currentPassword) {
    throw new BadRequest("Current password is required");
  }
  if (newPassword.length < 8) {
    throw new BadRequest("New password must be at least 8 characters");
  }

  const account = await db
    .prepare("SELECT public_id, nickname, password_salt, password_hash FROM local_accounts WHERE public_id = ?1")
    .bind(publicId)
    .first();
  if (!account || !(await verifyPassword(currentPassword, account.password_salt, account.password_hash))) {
    throw new BadRequest("UID or current password is incorrect");
  }

  const salt = randomBase64(16);
  const hash = await hashPassword(newPassword, salt);
  await db
    .prepare(
      `UPDATE local_accounts
       SET password_salt = ?2, password_hash = ?3, updated_at = CURRENT_TIMESTAMP
       WHERE public_id = ?1`
    )
    .bind(publicId, salt, hash)
    .run();

  const rk = await makeReceiverKeyHash(newPassword);
  await db
    .prepare(
      `UPDATE receiver_keys
       SET access_key = '', access_key_hash = ?2, access_key_salt = ?3, updated_at = CURRENT_TIMESTAMP
       WHERE guardian_handle = ?1`
    )
    .bind(publicId, rk.hash, rk.salt)
    .run();

  return { publicId: account.public_id, nickname: account.nickname };
}

async function hashPassword(password, saltBase64) {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey("raw", encoder.encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      hash: "SHA-256",
      salt: base64ToBytes(saltBase64),
      iterations: 100000,
    },
    key,
    256
  );
  return bytesToBase64(new Uint8Array(bits));
}

async function verifyPassword(password, saltBase64, expectedHash) {
  const actualHash = await hashPassword(password, saltBase64);
  return timingSafeEqual(actualHash, expectedHash);
}

function timingSafeEqual(a, b) {
  const left = new TextEncoder().encode(a);
  const right = new TextEncoder().encode(b);
  if (left.length !== right.length) return false;
  let diff = 0;
  for (let i = 0; i < left.length; i++) {
    diff |= left[i] ^ right[i];
  }
  return diff === 0;
}

function randomBase64(length) {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return bytesToBase64(bytes);
}

function bytesToBase64(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function base64ToBytes(value) {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

async function readJson(request) {
  try {
    const payload = await request.json();
    if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
      throw new BadRequest("JSON body must be an object");
    }
    return payload;
  } catch (error) {
    if (error instanceof BadRequest) throw error;
    throw new BadRequest("Invalid JSON");
  }
}

function syncIdFromUrl(url) {
  return url.searchParams.get("syncId") || url.searchParams.get("guardianHandle") || "";
}

function accessPasswordFromUrl(url) {
  return url.searchParams.get("accessPassword") || url.searchParams.get("accessKey") || "";
}

function viewerIdFromUrl(url) {
  return url.searchParams.get("viewerId") || url.searchParams.get("viewerUid") || "";
}

function viewerPasswordFromUrl(url) {
  return url.searchParams.get("viewerPassword") || "";
}

function viewAuthFromUrl(url) {
  return viewAuthFromPayload({
    viewerId: viewerIdFromUrl(url),
    viewerPassword: viewerPasswordFromUrl(url),
    accessPassword: accessPasswordFromUrl(url),
    accessKey: accessPasswordFromUrl(url),
  });
}

// 凭据优先从请求头读取，避免出现在 URL 查询串里（会落进访问日志/浏览器历史/Referer）。
// 仍保留 URL 兜底，兼容旧客户端过渡期。
function accessPasswordFrom(request, url) {
  return (request.headers.get("x-access-key") || "").trim() || accessPasswordFromUrl(url);
}
function viewerIdFrom(request, url) {
  return (request.headers.get("x-viewer-id") || "").trim() || viewerIdFromUrl(url);
}
function viewerPasswordFrom(request, url) {
  return request.headers.get("x-viewer-password") || viewerPasswordFromUrl(url);
}
function viewAuthFrom(request, url) {
  return viewAuthFromPayload({
    viewerId: viewerIdFrom(request, url),
    viewerPassword: viewerPasswordFrom(request, url),
    accessPassword: accessPasswordFrom(request, url),
    accessKey: accessPasswordFrom(request, url),
  });
}

function viewAuthFromPayload(payload) {
  return {
    viewerId: String(payload.viewerId || payload.viewerUid || "").trim().toUpperCase(),
    viewerPassword: String(payload.viewerPassword || ""),
    accessPassword: String(payload.accessPassword || payload.accessKey || ""),
  };
}

function normalizeNickname(value) {
  return String(value || "").trim();
}

async function getLocalAccount(db, publicId) {
  return db
    .prepare("SELECT public_id, nickname, password_salt, password_hash, account_role FROM local_accounts WHERE public_id = ?1")
    .bind(publicId)
    .first();
}

async function verifyViewerCredentials(db, viewerPublicId, password) {
  if (!viewerPublicId || !password) {
    throw new BadRequest("请先登录查看账号");
  }
  const account = await getLocalAccount(db, viewerPublicId);
  if (!account || !(await verifyPassword(password, account.password_salt, account.password_hash))) {
    throw new BadRequest("查看账号 UID 或密码不正确");
  }
  if ((account.account_role || "owner") !== "viewer") {
    throw new BadRequest("请使用网页注册的查看账号登录");
  }
  return {
    publicId: account.public_id,
    nickname: account.nickname,
    role: account.account_role || "viewer",
  };
}

async function isViewerAllowed(db, ownerPublicId, viewerNickname) {
  const allowed = await db
    .prepare(
      `SELECT id
       FROM owner_viewers
       WHERE owner_public_id = ?1
         AND lower(viewer_nickname) = lower(?2)`
    )
    .bind(ownerPublicId, normalizeNickname(viewerNickname))
    .first();
  return Boolean(allowed);
}

async function requireViewerViewAccess(db, ownerPublicId, auth) {
  const viewer = await verifyViewerCredentials(db, auth.viewerId, auth.viewerPassword);
  if (!(await isViewerAllowed(db, ownerPublicId, viewer.nickname))) {
    throw new BadRequest("您的昵称未在对方的查看名单中，请联系对方在手机端添加");
  }
  return viewer;
}

async function requireViewAccess(db, ownerPublicId, auth) {
  if (auth.viewerId && auth.viewerPassword) {
    return requireViewerViewAccess(db, ownerPublicId, auth);
  }
  await requireReceiverAccess(db, ownerPublicId, auth.accessPassword);
  return null;
}

function requireFields(payload, fields) {
  const missing = fields.filter((field) => payload[field] === undefined || payload[field] === null || payload[field] === "");
  if (missing.length) {
    throw new BadRequest(`Missing fields: ${missing.join(", ")}`);
  }
}

function parseLocalDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new BadRequest("localDate must use YYYY-MM-DD");
  }
  const date = new Date(`${value}T00:00:00.000Z`);
  if (Number.isNaN(date.getTime()) || formatDate(date) !== value) {
    throw new BadRequest("localDate must use YYYY-MM-DD");
  }
  return date;
}

function formatDate(date) {
  return date.toISOString().slice(0, 10);
}

function addDays(date, days) {
  const next = new Date(date.getTime());
  next.setUTCDate(next.getUTCDate() + days);
  return next;
}

function json(payload, status = 200) {
  return new Response(JSON.stringify(payload), { status, headers: JSON_HEADERS });
}

function html(markup, status = 200) {
  return new Response(markup, { status, headers: HTML_HEADERS });
}

function redirect(location, cookies = []) {
  const headers = new Headers({ location });
  for (const value of cookies) {
    headers.append("set-cookie", value);
  }
  return new Response(null, { status: 302, headers });
}

function cookie(name, value, maxAgeSeconds) {
  return `${name}=${encodeURIComponent(value)}; Max-Age=${maxAgeSeconds}; Path=/; HttpOnly; Secure; SameSite=Lax`;
}

function clearCookie(name) {
  return `${name}=; Max-Age=0; Path=/; HttpOnly; Secure; SameSite=Lax`;
}

function getCookie(request, name) {
  const header = request.headers.get("cookie") || "";
  const cookies = header.split(";").map((item) => item.trim());
  for (const item of cookies) {
    const index = item.indexOf("=");
    if (index === -1) continue;
    if (item.slice(0, index) === name) {
      return decodeURIComponent(item.slice(index + 1));
    }
  }
  return "";
}

class BadRequest extends Error {}
class TooManyRequests extends Error {}

// ---------- 安全基础设施（自建 schema，无需手动跑 migration）----------

let securitySchemaReady = false;

async function ensureSecuritySchema(db) {
  if (securitySchemaReady) {
    return;
  }
  const stmts = [
    // 若 receiver_keys 尚不存在则建全字段；已存在则靠下面的 ALTER 补列。
    `CREATE TABLE IF NOT EXISTS receiver_keys(
       guardian_handle TEXT PRIMARY KEY,
       access_key TEXT,
       access_key_hash TEXT,
       access_key_salt TEXT,
       updated_at TEXT
     )`,
    `ALTER TABLE receiver_keys ADD COLUMN access_key_hash TEXT`,
    `ALTER TABLE receiver_keys ADD COLUMN access_key_salt TEXT`,
    // 限流计数表（固定窗口）。
    `CREATE TABLE IF NOT EXISTS auth_attempts(
       bucket TEXT PRIMARY KEY,
       count INTEGER NOT NULL DEFAULT 0,
       window_start INTEGER NOT NULL DEFAULT 0
     )`,
  ];
  for (const sql of stmts) {
    try {
      await db.prepare(sql).run();
    } catch (error) {
      // "duplicate column name" / 已存在 等 → 忽略。
    }
  }
  securitySchemaReady = true;
}

/** 固定窗口限流。失败开放（表故障不阻断正常登录），只在超限时抛 429。 */
async function checkRateLimit(db, bucket, limit, windowSec) {
  const now = Math.floor(Date.now() / 1000);
  try {
    const row = await db
      .prepare("SELECT count, window_start FROM auth_attempts WHERE bucket = ?1")
      .bind(bucket)
      .first();
    if (!row || now - Number(row.window_start) >= windowSec) {
      await db
        .prepare(
          `INSERT INTO auth_attempts(bucket, count, window_start) VALUES(?1, 1, ?2)
           ON CONFLICT(bucket) DO UPDATE SET count = 1, window_start = ?2`
        )
        .bind(bucket, now)
        .run();
      return;
    }
    if (Number(row.count) >= limit) {
      throw new TooManyRequests("尝试过于频繁，请稍后再试");
    }
    await db.prepare("UPDATE auth_attempts SET count = count + 1 WHERE bucket = ?1").bind(bucket).run();
  } catch (error) {
    if (error instanceof TooManyRequests) {
      throw error;
    }
    // 其它异常（表缺失等）放行，避免因限流故障阻断正常使用。
  }
}

function clientBucket(request, tag) {
  const ip = request.headers.get("cf-connecting-ip") || request.headers.get("x-forwarded-for") || "unknown";
  return `${tag}:${ip}`;
}

// ---------- receiver_key 哈希（与账号密码同强度；兼容旧明文并自动升级）----------

async function makeReceiverKeyHash(accessKey) {
  const salt = randomBase64(16);
  const hash = await hashPassword(accessKey, salt);
  return { salt, hash };
}

async function verifyReceiverKeyRow(db, guardianHandle, row, accessKey) {
  if (!accessKey) {
    return false;
  }
  if (row.access_key_hash && row.access_key_salt) {
    return await verifyPassword(accessKey, row.access_key_salt, row.access_key_hash);
  }
  // 旧明文行：常量时间比较；命中则原地升级为哈希，并清空明文。
  if (row.access_key != null && row.access_key !== "") {
    const ok = timingSafeEqual(String(row.access_key), String(accessKey));
    if (ok) {
      try {
        const { salt, hash } = await makeReceiverKeyHash(accessKey);
        await db
          .prepare(
            "UPDATE receiver_keys SET access_key_hash = ?2, access_key_salt = ?3, access_key = '' WHERE guardian_handle = ?1"
          )
          .bind(guardianHandle, hash, salt)
          .run();
      } catch (error) {
        // 升级失败不影响本次校验结果。
      }
    }
    return ok;
  }
  return false;
}

function appPage(account) {
  if (!account) {
    return `<!doctype html>
<html lang="zh-CN">
<head>
  ${pageHead("UnlockHub")}
</head>
<body>
  <main>
    ${brandHeader("每日状态，安心可见", "注册时只需昵称和密码，系统会自动生成 UID。手机端同步状态，网页端用 UID + 密码查看。")}
    ${siteNav()}
    <div class="card actions">
      <a class="button" href="/register">注册新账号</a>
      <a class="button secondary" href="/login">登录已有账号</a>
    </div>
    <p class="sub">每位用户独立注册，互不影响。</p>
  </main>
</body>
</html>`;
  }
  return `<!doctype html>
<html lang="zh-CN">
<head>
  ${pageHead("UnlockHub")}
</head>
<body>
  <main>
    ${brandHeader("我的账号", `已登录：${escapeHtml(account.email)}`)}
    ${siteNav()}
    <div class="card">
      <h2>账号信息</h2>
      <pre>UnlockHub ID：${escapeHtml(account.public_id)}
昵称：${escapeHtml(account.name || "未设置")}</pre>
    </div>
    <form method="post" action="/auth/logout">
      <button class="button" type="submit">退出登录</button>
    </form>
  </main>
</body>
</html>`;
}

function registerPage() {
  return `<!doctype html>
<html lang="zh-CN">
<head>
  ${pageHead("UnlockHub 注册")}
</head>
<body>
  <main>
    <div class="page-top">
      ${brandHeader("注册查看账号", "填写昵称和密码即可。注册后请保存 UID，登录时使用 UID + 密码。")}
      <div id="authCorner" class="auth-corner"><a href="/login">登录</a> · <a href="/">查询页</a></div>
    </div>
    <div class="card">
      <label>昵称</label>
      <input id="nickname" placeholder="例如：妈妈" autocomplete="nickname">
      <p class="hint">请与对方（手机端）确认使用相同昵称，对方会把你加入查看名单。</p>
      <label>密码</label>
      <input id="password" placeholder="至少 8 位" type="password" autocomplete="new-password">
      <label>确认密码</label>
      <input id="confirmPassword" placeholder="再次输入密码" type="password" autocomplete="new-password">
      <button class="button" id="register">注册并登录</button>
    </div>
    <p class="sub">已有账号？<a href="/login">登录</a></p>
    <div id="result" class="card" style="display:none"></div>
  </main>
  <script>${credentialsScript()}</script>
  <script>
    const nicknameInput = document.getElementById('nickname');
    const passwordInput = document.getElementById('password');
    const confirmPasswordInput = document.getElementById('confirmPassword');
    const result = document.getElementById('result');
    const returnSyncId = new URLSearchParams(location.search).get('syncId') || '';
    document.getElementById('register').addEventListener('click', register);
    async function register() {
      if (passwordInput.value.length < 8) {
        result.style.display = 'block';
        result.textContent = '密码至少 8 位。';
        return;
      }
      if (passwordInput.value !== confirmPasswordInput.value) {
        result.style.display = 'block';
        result.textContent = '两次输入的密码不一致。';
        return;
      }
      result.style.display = 'block';
      result.textContent = '正在注册...';
      try {
        const response = await fetch('/api/register', {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({
            nickname: nicknameInput.value.trim(),
            password: passwordInput.value,
            role: 'viewer'
          })
        });
        const data = await response.json();
        if (!response.ok) throw new Error(data.error || '注册失败');
        saveCredentials(data.account.publicId, passwordInput.value, data.account.nickname);
        const target = '/' + (returnSyncId ? '?syncId=' + encodeURIComponent(returnSyncId) : '');
        location.href = target;
      } catch (error) {
        result.textContent = error.message;
      }
    }
  </script>
</body>
</html>`;
}

function loginPage() {
  return `<!doctype html>
<html lang="zh-CN">
<head>
  ${pageHead("UnlockHub 登录")}
</head>
<body>
  <main>
    <div class="page-top">
      ${brandHeader("登录", "使用注册时获得的 UID 和密码。浏览器会记住，下次自动登录。")}
      <div class="auth-corner"><a href="/register">注册</a> · <a href="/">查询页</a></div>
    </div>
    <form class="card" id="loginForm" autocomplete="on" onsubmit="return false;">
      <label for="uid">我的 UID</label>
      <input id="uid" name="username" placeholder="例如：SP-ABCD-1234" autocomplete="username">
      <label for="password">密码</label>
      <input id="password" name="password" placeholder="注册时设置的密码" type="password" autocomplete="current-password">
      <button class="button" id="login" type="submit">登录</button>
    </form>
    <p class="sub">还没有账号？<a href="/register">注册查看账号</a> · <a href="/forgot-uid">忘记 UID？</a></p>
    <div id="result" class="card" style="display:none"></div>
  </main>
  <script>${credentialsScript()}</script>
  <script>
    const uidInput = document.getElementById('uid');
    const passwordInput = document.getElementById('password');
    const result = document.getElementById('result');
    const saved = loadCredentials();
    const querySyncId = new URLSearchParams(location.search).get('syncId') || '';
    uidInput.value = saved.uid || '';
    passwordInput.value = saved.password || '';
    document.getElementById('loginForm').addEventListener('submit', login);
    async function login() {
      result.style.display = 'block';
      result.textContent = '正在登录...';
      try {
        const response = await fetch('/api/login', {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({
            publicId: uidInput.value.trim(),
            password: passwordInput.value
          })
        });
        const data = await response.json();
        if (!response.ok) throw new Error(data.error || '登录失败');
        if (data.account.role && data.account.role !== 'viewer') {
          throw new Error('请使用网页注册的查看账号登录');
        }
        saveCredentials(data.account.publicId, passwordInput.value, data.account.nickname);
        location.href = '/' + (querySyncId ? '?syncId=' + encodeURIComponent(querySyncId) : '');
      } catch (error) {
        result.textContent = error.message;
      }
    }
  </script>
</body>
</html>`;
}

function forgotUidPage() {
  return `<!doctype html>
<html lang="zh-CN">
<head>
  ${pageHead("UnlockHub 找回 UID")}
</head>
<body>
  <main>
    <div class="page-top">
      ${brandHeader("找回 UID", "填写您的昵称、孩子的昵称和密码。系统会核对三方信息后返回您的 UID。")}
      <div class="auth-corner"><a href="/login">返回登录</a></div>
    </div>
    <div class="card">
      <label for="viewerNickname">您的昵称</label>
      <input id="viewerNickname" placeholder="例如：妈妈" autocomplete="nickname">
      <label for="ownerNickname">孩子的昵称</label>
      <input id="ownerNickname" placeholder="孩子手机端注册时填写的昵称" autocomplete="off">
      <p class="hint">孩子的昵称用于确认您要查看的是谁的状态。找回后，孩子也可在手机端「查看人昵称」列表中看到您的 UID 并复制分享。</p>
      <label for="password">密码</label>
      <input id="password" placeholder="注册时设置的密码" type="password" autocomplete="current-password">
      <button class="button" id="recover" type="button">找回 UID</button>
    </div>
    <div id="result" class="card" style="display:none"></div>
  </main>
  <script>${credentialsScript()}</script>
  <script>
    const result = document.getElementById('result');
    document.getElementById('recover').addEventListener('click', recoverUid);
    async function recoverUid() {
      result.style.display = 'block';
      result.textContent = '正在核对信息...';
      try {
        const response = await fetch('/api/recover-uid', {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({
            viewerNickname: document.getElementById('viewerNickname').value.trim(),
            ownerNickname: document.getElementById('ownerNickname').value.trim(),
            password: document.getElementById('password').value
          })
        });
        const data = await response.json();
        if (!response.ok) throw new Error(data.error || '找回失败');
        saveCredentials(data.account.publicId, document.getElementById('password').value, data.account.nickname);
        result.innerHTML = '<h2>找回成功</h2><pre>您的 UID：' + escapeHtml(data.account.publicId) +
          '\\n昵称：' + escapeHtml(data.account.nickname) +
          '\\n孩子：' + escapeHtml(data.account.ownerNickname || '') +
          '</pre><p class="sub"><a href="/login">去登录</a> · <a href="/">去查询页</a></p>';
      } catch (error) {
        result.textContent = error.message;
      }
    }
    function escapeHtml(value) {
      return String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
    }
  </script>
</body>
</html>`;
}

function profilePage() {
  return `<!doctype html>
<html lang="zh-CN">
<head>
  ${pageHead("UnlockHub 个人资料")}
</head>
<body>
  <main>
    <div class="page-top">
      ${brandHeader("个人资料", "查看账号信息，或修改登录密码。")}
      <div class="auth-corner"><a href="/">返回查询</a></div>
    </div>
    <div class="card profile-card">
      <div class="avatar" aria-hidden="true">${logoSvg()}</div>
      <div>
        <h2 id="nickname">未登录</h2>
        <pre id="uid">UID：-</pre>
      </div>
    </div>
    <form class="card" id="passwordForm" autocomplete="on" onsubmit="return false;">
      <h2>修改密码</h2>
      <label for="currentPassword">当前密码</label>
      <input id="currentPassword" name="password" type="password" autocomplete="current-password">
      <label for="newPassword">新密码</label>
      <input id="newPassword" name="new-password" type="password" autocomplete="new-password" placeholder="至少 8 位">
      <label for="confirmPassword">确认新密码</label>
      <input id="confirmPassword" type="password" autocomplete="new-password">
      <button class="button" type="submit">保存新密码</button>
    </form>
    <div id="result" class="card" style="display:none"></div>
    <div id="accountBar" class="account-bar" hidden>
      <button class="button secondary" id="logout" type="button">退出登录</button>
    </div>
  </main>
  <script>${credentialsScript()}</script>
  <script>
    let saved = loadCredentials();
    const nicknameEl = document.getElementById('nickname');
    const uidEl = document.getElementById('uid');
    const currentPasswordInput = document.getElementById('currentPassword');
    const result = document.getElementById('result');
    const accountBar = document.getElementById('accountBar');
    function renderSession() {
      saved = loadCredentials();
      if (!saved.uid) {
        location.href = '/login';
        return;
      }
      nicknameEl.textContent = saved.nickname || 'UnlockHub 用户';
      uidEl.textContent = 'UID：' + saved.uid;
      currentPasswordInput.value = saved.password || '';
      accountBar.hidden = false;
    }
    renderSession();
    document.getElementById('passwordForm').addEventListener('submit', changePassword);
    document.getElementById('logout').addEventListener('click', () => {
      clearCredentials();
      location.href = '/login';
    });
    async function changePassword() {
      const newPassword = document.getElementById('newPassword').value;
      const confirmPassword = document.getElementById('confirmPassword').value;
      if (!saved.uid) {
        result.style.display = 'block';
        result.textContent = '请先登录后再修改密码。';
        return;
      }
      if (newPassword.length < 8) {
        result.style.display = 'block';
        result.textContent = '新密码至少 8 位。';
        return;
      }
      if (newPassword !== confirmPassword) {
        result.style.display = 'block';
        result.textContent = '两次输入的新密码不一致。';
        return;
      }
      result.style.display = 'block';
      result.textContent = '正在保存...';
      try {
        const response = await fetch('/api/change-password', {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({
            publicId: saved.uid,
            currentPassword: currentPasswordInput.value,
            newPassword
          })
        });
        const data = await response.json();
        if (!response.ok) throw new Error(data.error || '修改失败');
        saveCredentials(saved.uid, newPassword, data.account.nickname || saved.nickname);
        currentPasswordInput.value = newPassword;
        document.getElementById('newPassword').value = '';
        document.getElementById('confirmPassword').value = '';
        result.textContent = '密码已更新。';
      } catch (error) {
        result.textContent = error.message;
      }
    }
  </script>
</body>
</html>`;
}

function credentialsScript() {
  return `
    const CRED_UID = 'safePingHandle';
    const CRED_PASS = 'safePingAccessKey';
    const CRED_NAME = 'safePingNickname';
    function saveCredentials(uid, password, nickname) {
      localStorage.setItem(CRED_UID, uid);
      localStorage.setItem(CRED_PASS, password);
      if (nickname) localStorage.setItem(CRED_NAME, nickname);
    }
    function loadCredentials() {
      return {
        uid: localStorage.getItem(CRED_UID) || '',
        password: localStorage.getItem(CRED_PASS) || '',
        nickname: localStorage.getItem(CRED_NAME) || ''
      };
    }
    function clearCredentials() {
      localStorage.removeItem(CRED_UID);
      localStorage.removeItem(CRED_PASS);
      localStorage.removeItem(CRED_NAME);
    }
  `;
}

function logoSvg() {
  return `<svg class="brand-logo" viewBox="0 0 48 48" aria-hidden="true" focusable="false">
    <defs>
      <linearGradient id="shield" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stop-color="#1f8f89"/>
        <stop offset="100%" stop-color="#176d6a"/>
      </linearGradient>
    </defs>
    <path fill="url(#shield)" d="M24 6 36 12v12c0 7.5-5.5 13-12 15.5C17.5 37 12 31.5 12 24V12Z"/>
    <circle cx="24" cy="22" r="9" fill="none" stroke="#b8e0dc" stroke-width="2"/>
    <circle cx="24" cy="22" r="4.5" fill="#f7f3ea"/>
  </svg>`;
}

function faviconTag() {
  const svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><path fill="%23176D6A" d="M24 6 36 12v12c0 7.5-5.5 13-12 15.5C17.5 37 12 31.5 12 24V12Z"/><circle cx="24" cy="22" r="4.5" fill="%23F7F3EA"/></svg>';
  return `<link rel="icon" href="data:image/svg+xml,${encodeURIComponent(svg)}">`;
}

function i18nScript() {
  return `<script>
(function () {
  var DICT = {
    // 服务端 API 错误消息
    "该昵称已在查看名单中": "That nickname is already in the viewer list",
    "查看人不存在": "Viewer not found",
    "此 UID 尚未设置访问密钥，无法查看": "This UID has no access key set, so it cannot be viewed",
    "UID 或密码不正确": "Incorrect UID or password",
    "昵称或密码不正确": "Incorrect nickname or password",
    "请填写您的昵称": "Enter your nickname",
    "请填写孩子的昵称": "Enter their nickname",
    "请填写密码": "Enter your password",
    "请使用网页注册的查看账号": "Use a viewer account registered on the web",
    "未找到对应的孩子账号，请确认孩子昵称": "No matching account found; check their nickname",
    "您的昵称未在对方的查看名单中，请联系孩子在手机端添加": "Your nickname isn't on their viewer list; ask them to add it on the phone",
    "该昵称对应多个账号，请联系对方确认": "That nickname matches multiple accounts; please confirm with them",
    "请先登录查看账号": "Sign in to a viewer account first",
    "查看账号 UID 或密码不正确": "Incorrect viewer UID or password",
    "请使用网页注册的查看账号登录": "Sign in with a viewer account registered on the web",
    "您的昵称未在对方的查看名单中，请联系对方在手机端添加": "Your nickname isn't on their viewer list; ask them to add it on the phone",
    "尝试过于频繁，请稍后再试": "Too many attempts; please try again later",
    // 通用/导航
    "查询页": "Status", "注册": "Register", "登录": "Sign in", "个人资料": "Profile",
    "返回登录": "Back to sign in", "返回查询": "Back to status", "去登录": "Sign in", "去查询页": "Status page",
    // 首页
    "每日状态，安心可见": "Daily status, always visible",
    "注册时只需昵称和密码，系统会自动生成 UID。手机端同步状态，网页端用 UID + 密码查看。":
      "Registration needs only a nickname and password; a UID is generated automatically. The phone syncs status; view it here with UID + password.",
    "注册新账号": "Create an account", "登录已有账号": "Sign in to an existing account",
    "每位用户独立注册，互不影响。": "Each user registers independently.",
    // 注册页
    "注册查看账号": "Create a viewer account",
    "填写昵称和密码即可。注册后请保存 UID，登录时使用 UID + 密码。":
      "Just fill in a nickname and password. Save the UID afterwards — sign in with UID + password.",
    "昵称": "Nickname", "密码": "Password", "确认密码": "Confirm password",
    "例如：妈妈": "e.g. Mom", "至少 8 位": "At least 8 characters", "再次输入密码": "Enter the password again",
    "请与对方（手机端）确认使用相同昵称，对方会把你加入查看名单。":
      "Confirm the same nickname with the phone user; they will add you to their viewer list.",
    "注册并登录": "Register and sign in", "已有账号？": "Already have an account?",
    "密码至少 8 位。": "Password needs at least 8 characters.",
    "两次输入的密码不一致。": "The two passwords don't match.",
    "正在注册...": "Registering…", "注册失败": "Registration failed",
    // 登录页
    "使用注册时获得的 UID 和密码。浏览器会记住，下次自动登录。":
      "Use the UID and password from registration. Your browser remembers them for next time.",
    "我的 UID": "My UID", "例如：SP-ABCD-1234": "e.g. SP-ABCD-1234",
    "注册时设置的密码": "The password you set at registration",
    "还没有账号？": "No account yet?", "忘记 UID？": "Forgot UID?",
    "正在登录...": "Signing in…", "登录失败": "Sign-in failed",
    "请使用网页注册的查看账号登录": "Sign in with a viewer account registered on the web",
    // 找回 UID
    "找回 UID": "Recover UID",
    "填写您的昵称、孩子的昵称和密码。系统会核对三方信息后返回您的 UID。":
      "Enter your nickname, the phone user's nickname and your password. We'll verify and return your UID.",
    "您的昵称": "Your nickname", "孩子的昵称": "Their nickname",
    "孩子手机端注册时填写的昵称": "The nickname they registered with on the phone",
    "孩子的昵称用于确认您要查看的是谁的状态。找回后，孩子也可在手机端「查看人昵称」列表中看到您的 UID 并复制分享。":
      "Their nickname confirms whose status you want to view. Afterwards they can also see and share your UID from the phone's viewer list.",
    "正在核对信息...": "Verifying…", "找回失败": "Recovery failed", "找回成功": "Recovered",
    "您的 UID：": "Your UID: ", "孩子：": "Their nickname: ",
    // 个人资料
    "查看账号信息，或修改登录密码。": "View your account or change your password.",
    "未登录": "Not signed in", "账号信息": "Account", "修改密码": "Change password",
    "当前密码": "Current password", "新密码": "New password", "确认新密码": "Confirm new password",
    "保存新密码": "Save new password", "退出登录": "Sign out",
    "请先登录后再修改密码。": "Sign in before changing your password.",
    "新密码至少 8 位。": "The new password needs at least 8 characters.",
    "两次输入的新密码不一致。": "The new passwords don't match.",
    "正在保存...": "Saving…", "修改失败": "Update failed", "密码已更新。": "Password updated.",
    "UnlockHub 用户": "UnlockHub user", "未设置": "not set",
    // 查询页
    "查询": "Status", "对方 UID": "Their UID", "读取失败": "Load failed",
    "登录查看账号后，输入对方的 UID 查询同步状态。打开分享链接会自动填入对方 UID。":
      "Sign in with a viewer account, then enter their UID to see the synced status. A shared link fills the UID automatically.",
    "请输入对方的 UID。": "Enter their UID.",
    "请先登录或注册查看账号。": "Sign in or register a viewer account first.",
    "正在读取...": "Loading…", "读取摘要失败": "Failed to load the summary",
    "读取消息失败": "Failed to load messages", "读取记录失败": "Failed to load records",
    "状态摘要": "Status summary", "备忘录": "Memos", "对方": "They",
    "还没有消息。": "No messages yet.", "已读": "read", "未读": "unread",
    "还没有活动记录。": "No activity yet.", "未知": "unknown",
    "超过 72 小时没有新的首次解锁记录": "No new first-unlock record for over 72 hours",
    "最近有首次解锁记录": "Recent first-unlock records exist",
    "对方昵称：": "Their nickname: ", "我的昵称：": "My nickname: ", "状态：": "Status: ",
    "最近记录的首次解锁：": "Last recorded first unlock: ", "距这条记录：": "Since that record: ",
    "我的未读消息：": "My unread messages: ", "小时": " hours",
    "的状态记录": "'s status records", "还没有已完成周期的解锁记录。": "No completed sync periods yet.",
    "完整": "complete", "缺失": "missing",
    "对方共享的备忘（私密备忘不会同步）。": "Memos shared by them (private memos are never synced).",
    "（无标题）": "(Untitled)", "（空清单）": "(Empty checklist)",
    "置顶": "Pinned", "已完成": "Done", "至": " – ",
    "每段为一个同步周期（走完才显示）。": "Each block is one sync period (shown once complete).",
    "表示该周期每天都有解锁记录；": " means every day in the period has an unlock record; ",
    "列出当天没有记录的日期（连续缺失可能意味着手机关机/无网/未使用）。":
      " lists days without records (a long gap may mean the phone was off, offline or unused)."
  };
  var lang = null;
  try { lang = localStorage.getItem('uh_lang'); } catch (e) {}
  if (!lang) { lang = (navigator.language || '').toLowerCase().indexOf('zh') === 0 ? 'zh' : 'en'; }

  function tr(text) {
    if (lang !== 'en' || !text) return text;
    var trimmed = text.trim();
    if (!trimmed) return text;
    if (DICT[trimmed]) return text.replace(trimmed, DICT[trimmed]);
    var out = text;
    for (var k in DICT) {
      if (out.indexOf(k) >= 0) out = out.split(k).join(DICT[k]);
    }
    return out;
  }
  window.__t = tr;

  function walk(root) {
    if (lang !== 'en' || !root) return;
    var it = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
    var nodes = [];
    while (it.nextNode()) nodes.push(it.currentNode);
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i];
      if (n.parentNode && n.parentNode.tagName === 'SCRIPT') continue;
      var v = tr(n.nodeValue);
      if (v !== n.nodeValue) n.nodeValue = v;
    }
    var inputs = root.querySelectorAll ? root.querySelectorAll('input[placeholder]') : [];
    for (var j = 0; j < inputs.length; j++) {
      inputs[j].placeholder = tr(inputs[j].placeholder);
    }
    if (root === document.body) document.title = tr(document.title);
  }

  function addSwitcher() {
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.id = 'uhLangBtn';
    btn.textContent = lang === 'en' ? '中文' : 'EN';
    btn.setAttribute('style',
      'position:fixed;top:10px;right:10px;z-index:9999;padding:6px 12px;border-radius:999px;' +
      'border:1px solid rgba(0,0,0,.15);background:#fffdf8;color:#333;font-size:13px;cursor:pointer;');
    btn.onclick = function () {
      var next = lang === 'en' ? 'zh' : 'en';
      try { localStorage.setItem('uh_lang', next); } catch (e) {}
      location.reload();
    };
    document.body.appendChild(btn);
  }

  function boot() {
    walk(document.body);
    addSwitcher();
    // 动态渲染的内容也翻译（查询结果等）
    var mo = new MutationObserver(function (muts) {
      if (lang !== 'en') return;
      for (var i = 0; i < muts.length; i++) {
        var added = muts[i].addedNodes;
        for (var j = 0; j < added.length; j++) {
          if (added[j].nodeType === 1) walk(added[j]);
          else if (added[j].nodeType === 3) {
            var v = tr(added[j].nodeValue);
            if (v !== added[j].nodeValue) added[j].nodeValue = v;
          }
        }
        if (muts[i].type === 'characterData') {
          var t = muts[i].target;
          var nv = tr(t.nodeValue);
          if (nv !== t.nodeValue) t.nodeValue = nv;
        }
      }
    });
    mo.observe(document.body, { childList: true, subtree: true, characterData: true });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
<\/script>`;
}

function pageHead(title) {
  return `<meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${escapeHtml(title)}</title>
  ${faviconTag()}
  <style>${baseCss()}</style>
  ${i18nScript()}`;
}

function brandHeader(pageTitle, subtitleHtml = "") {
  return `<header class="brand">
    ${logoSvg()}
    <div class="brand-copy">
      <div class="brand-kicker">UnlockHub</div>
      <h1>${escapeHtml(pageTitle)}</h1>
      ${subtitleHtml ? `<p class="sub">${subtitleHtml}</p>` : ""}
    </div>
  </header>`;
}

function siteNav() {
  return `<nav class="nav">
    <a href="/">查询页</a>
    <a href="/register">注册</a>
    <a href="/login">登录</a>
    <a href="/profile">个人资料</a>
  </nav>`;
}

function baseCss() {
  return `
    :root {
      color-scheme: light;
      --bg: #f3efe4;
      --bg-accent: #e8f3f1;
      --panel: #fffdf8;
      --text: #1f2421;
      --muted: #66706a;
      --line: #ddd5c6;
      --accent: #176d6a;
      --accent-soft: #b8e0dc;
      --alert: #9b2c2c;
      --shadow: 0 14px 40px rgba(23, 36, 33, 0.08);
      --radius: 16px;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      min-height: 100vh;
      background:
        radial-gradient(circle at top left, rgba(184, 224, 220, 0.45), transparent 32%),
        radial-gradient(circle at top right, rgba(247, 243, 234, 0.9), transparent 28%),
        linear-gradient(180deg, var(--bg-accent) 0%, var(--bg) 42%, #f7f3ea 100%);
      color: var(--text);
      font-family: "Segoe UI", system-ui, -apple-system, BlinkMacSystemFont, sans-serif;
      line-height: 1.5;
    }
    main { width: min(720px, calc(100% - 32px)); margin: 0 auto; padding: 28px 0 48px; }
    .brand {
      display: flex;
      gap: 16px;
      align-items: center;
      margin-bottom: 20px;
      padding: 18px 20px;
      border: 1px solid rgba(221, 213, 198, 0.9);
      border-radius: calc(var(--radius) + 4px);
      background: linear-gradient(135deg, rgba(255,255,255,0.96), rgba(247,243,234,0.88));
      box-shadow: var(--shadow);
    }
    .brand-logo { width: 56px; height: 56px; flex: 0 0 auto; }
    .brand-kicker {
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.12em;
      text-transform: uppercase;
      color: var(--accent);
    }
    h1 { margin: 4px 0 0; font-size: clamp(28px, 4vw, 34px); letter-spacing: -0.02em; }
    h2 { margin: 0 0 8px; font-size: 18px; }
    .sub { margin: 8px 0 0; color: var(--muted); line-height: 1.6; }
    .sub a, .nav a { color: var(--accent); font-weight: 600; text-decoration: none; }
    .sub a:hover, .nav a:hover { text-decoration: underline; }
    .miss { color: #d9534f; font-weight: 700; }
    .ok { color: #3a9d6e; font-weight: 600; }
    .nav { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 18px; }
    .nav a {
      display: inline-flex;
      align-items: center;
      min-height: 38px;
      padding: 0 14px;
      border: 1px solid var(--line);
      border-radius: 999px;
      background: rgba(255,255,255,0.72);
    }
    .card, .message {
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: var(--radius);
      padding: 18px;
      margin: 14px 0;
      box-shadow: 0 8px 24px rgba(31, 36, 33, 0.05);
    }
    .card.alert, .message.alert {
      border-color: #ddb1aa;
      background: linear-gradient(180deg, #fff8f6, #fffdf8);
    }
    label { display:block; margin: 12px 0 6px; font-weight: 700; font-size: 13px; color: var(--muted); }
    input {
      width: 100%;
      min-height: 48px;
      border: 1px solid var(--line);
      border-radius: 12px;
      font: inherit;
      padding: 0 14px;
      background: #fff;
      transition: border-color 0.15s ease, box-shadow 0.15s ease;
    }
    input:focus {
      outline: none;
      border-color: var(--accent);
      box-shadow: 0 0 0 3px rgba(23, 109, 106, 0.12);
    }
    pre { margin: 0; white-space: pre-wrap; word-break: break-word; font: inherit; line-height: 1.55; }
    .meta { color: var(--muted); font-size: 13px; margin-bottom: 12px; }
    .button, button.button, a.button {
      display: inline-flex;
      min-height: 48px;
      align-items: center;
      justify-content: center;
      padding: 0 18px;
      margin-top: 12px;
      margin-right: 8px;
      background: linear-gradient(180deg, #1f8f89, var(--accent));
      color: white;
      border: 1px solid var(--accent);
      border-radius: 12px;
      font: inherit;
      font-weight: 600;
      text-decoration: none;
      cursor: pointer;
      box-shadow: 0 8px 18px rgba(23, 109, 106, 0.18);
    }
    .button.secondary, button.secondary, a.button.secondary {
      background: #fff;
      color: var(--accent);
      box-shadow: none;
    }
    .toolbar { display: grid; grid-template-columns: 1fr 1fr auto; gap: 10px; margin-bottom: 18px; align-items: end; }
    .toolbar input, .toolbar button { margin-top: 0; }
    .status { min-height: 24px; color: var(--muted); margin-bottom: 12px; }
    .empty {
      border: 1px dashed var(--line);
      border-radius: var(--radius);
      padding: 28px;
      color: var(--muted);
      text-align: center;
      background: rgba(255,255,255,0.55);
    }
    .avatar {
      width: 72px;
      height: 72px;
      border-radius: 50%;
      border: 2px solid var(--accent-soft);
      background: radial-gradient(circle at 30% 30%, #fff, #ece7dc);
      display: grid;
      place-items: center;
      flex: 0 0 auto;
    }
    .avatar svg { width: 34px; height: 34px; }
    .profile-card { display:flex; gap:16px; align-items:center; }
    .actions { display: flex; flex-wrap: wrap; gap: 8px; }
    .page-top { display:flex; gap:16px; align-items:flex-start; justify-content:space-between; margin-bottom: 8px; }
    .page-top .brand { flex: 1; margin-bottom: 0; }
    .auth-corner { text-align:right; padding-top: 8px; font-size: 14px; white-space: nowrap; }
    .auth-corner a { color: var(--accent); font-weight: 600; text-decoration: none; }
    .auth-corner a:hover { text-decoration: underline; }
    .auth-name { color: var(--text); font-weight: 700; text-decoration: none; }
    a.auth-name:hover { color: var(--accent); text-decoration: underline; }
    .hint { margin: 4px 0 0; color: var(--muted); font-size: 13px; line-height: 1.5; }
    .query-toolbar { display:grid; grid-template-columns: 1fr auto; gap: 10px; align-items: end; }
    .read-badge { display:inline-block; padding: 2px 8px; border-radius: 999px; font-size: 12px; font-weight: 700; }
    .read-badge.read { background: #e8f3f1; color: var(--accent); }
    .read-badge.unread { background: #fff1e8; color: #9b4d2c; }
    .account-bar {
      margin-top: 28px;
      padding-top: 20px;
      border-top: 1px solid var(--line);
      display: flex;
      justify-content: center;
    }
    .account-bar .button { margin-top: 0; min-width: 160px; }
    @media (max-width: 520px) {
      main { width: min(100% - 24px, 720px); padding-top: 20px; }
      .toolbar, .query-toolbar { grid-template-columns: 1fr; }
      .toolbar button, .query-toolbar button { width: 100%; }
      .page-top { flex-direction: column; }
      .auth-corner { text-align: left; padding-top: 0; }
      .brand { flex-direction: column; align-items: flex-start; }
    }
  `;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

export function inboxPage() {
  return `<!doctype html>
<html lang="zh-CN">
<head>
  ${pageHead("UnlockHub 查询页")}
</head>
<body>
  <main>
    <div class="page-top">
      ${brandHeader("查询", "登录查看账号后，输入对方的 UID 查询同步状态。打开分享链接会自动填入对方 UID。")}
      <div id="authCorner" class="auth-corner"></div>
    </div>
    <form class="query-toolbar card" id="accessForm" autocomplete="on" onsubmit="return false;">
      <div>
        <label for="handle">对方 UID</label>
        <input id="handle" name="ownerSyncId" placeholder="例如：SP-ABCD-1234" autocomplete="off">
      </div>
      <button id="load" type="submit">查询</button>
    </form>
    <div id="status" class="status"></div>
    <section id="messages"></section>
  </main>
  <script>
    const handleInput = document.getElementById('handle');
    const status = document.getElementById('status');
    const messages = document.getElementById('messages');
    const authCorner = document.getElementById('authCorner');
    ${credentialsScript()}
    let saved = loadCredentials();
    const queryHandle = new URLSearchParams(location.search).get('syncId') || new URLSearchParams(location.search).get('guardianHandle') || '';
    handleInput.value = queryHandle || localStorage.getItem('safePingLastOwnerSyncId') || '';
    renderAuthCorner();
    document.getElementById('accessForm').addEventListener('submit', loadMessages);
    if (saved.uid && saved.password && handleInput.value) loadMessages();
    function renderAuthCorner() {
      saved = loadCredentials();
      if (!saved.uid || !saved.password) {
        const loginHref = '/login' + (handleInput.value ? '?syncId=' + encodeURIComponent(handleInput.value) : '');
        const registerHref = '/register' + (handleInput.value ? '?syncId=' + encodeURIComponent(handleInput.value) : '');
        authCorner.innerHTML = '<a href="' + loginHref + '">登录</a> · <a href="' + registerHref + '">注册</a>';
        return;
      }
      authCorner.innerHTML = '<a class="auth-name" href="/profile">' + escapeHtml(saved.nickname || saved.uid) + '</a>';
    }
    // 只把非机密的 UID 放 query；凭据走请求头，避免进访问日志/浏览器历史/Referer。
    function viewerQuery(ownerHandle) {
      return 'syncId=' + encodeURIComponent(ownerHandle);
    }
    function viewerHeaders() {
      return { 'x-viewer-id': saved.uid, 'x-viewer-password': saved.password };
    }
    async function loadMessages() {
      saved = loadCredentials();
      const handle = handleInput.value.trim().toUpperCase();
      if (!handle) { status.textContent = '请输入对方的 UID。'; return; }
      if (!saved.uid || !saved.password) {
        status.textContent = '请先登录或注册查看账号。';
        renderAuthCorner();
        return;
      }
      localStorage.setItem('safePingLastOwnerSyncId', handle);
      status.textContent = '正在读取...';
      messages.innerHTML = '';
      try {
        const query = viewerQuery(handle);
        const headers = viewerHeaders();
        const [summaryResponse, messageResponse, eventResponse, memoResponse] = await Promise.all([
          fetch('/api/summary?' + query, { headers }),
          fetch('/api/messages?' + query, { headers }),
          fetch('/api/unlock-events?' + query, { headers }),
          fetch('/api/memos?' + query, { headers })
        ]);
        const summaryData = await summaryResponse.json();
        const messageData = await messageResponse.json();
        const eventData = await eventResponse.json();
        const memoData = memoResponse.ok ? await memoResponse.json() : { memos: [] };
        if (!summaryResponse.ok) throw new Error(summaryData.error || '读取摘要失败');
        if (!messageResponse.ok) throw new Error(messageData.error || '读取消息失败');
        if (!eventResponse.ok) throw new Error(eventData.error || '读取记录失败');
        const messageItems = messageData.messages || [];
        const memoItems = memoData.memos || [];
        renderInbox(summaryData.summary, messageItems, eventData.events || [], saved.nickname, memoItems);
        markVisibleMessagesRead(handle, messageItems);
        status.textContent = '以「' + (saved.nickname || saved.uid) + '」查看 · 共 ' + messageItems.length + ' 条消息，' + (eventData.events || []).length + ' 条解锁记录，' + memoItems.length + ' 条备忘';
      } catch (error) {
        status.textContent = error.message;
      }
    }
    function renderInbox(summary, messageItems, eventItems, viewerNickname, memoItems) {
      const summaryHtml = renderSummary(summary, viewerNickname);
      const statusHtml = renderStatusPeriods(summary);
      const visibleMessages = (messageItems || []).filter(item => item.type !== 'weekly_report');
      messages.innerHTML = summaryHtml + statusHtml + renderMemosHtml(memoItems || []) + renderMessagesHtml(visibleMessages);
    }
    function renderStatusPeriods(summary) {
      const periods = (summary && summary.statusPeriods) || [];
      const owner = summary && summary.ownerNickname ? summary.ownerNickname : '对方';
      if (!periods.length) {
        return '<div class="message"><h2>' + escapeHtml(owner) + ' 的状态记录</h2><pre>还没有已完成周期的解锁记录。</pre></div>';
      }
      const blocks = periods.map(p => {
        if (p.periodStart === p.periodEnd) return escapeHtml(p.periodStart);
        const header = escapeHtml(p.periodStart + ' 至 ' + p.periodEnd);
        const miss = missingRuns(p.days || []);
        if (!miss.length) return header + ' <span class="ok">· 完整</span>';
        return header + '\\n    <span class="miss">缺失 ' + miss.map(escapeHtml).join('、') + '</span>';
      }).join('\\n\\n');
      return '<div class="message"><h2>' + escapeHtml(owner) + ' 的状态记录</h2>' +
        '<pre>' + blocks + '</pre>' +
        '<p class="sub">每段为一个同步周期（走完才显示）。<span class="ok">· 完整</span> 表示该周期每天都有解锁记录；<span class="miss">缺失 …</span> 列出当天没有记录的日期（连续的合并为区间）。</p></div>';
    }
    function missingRuns(days) {
      const runs = [];
      let i = 0;
      while (i < days.length) {
        if (days[i].has) { i++; continue; }
        let j = i;
        while (j + 1 < days.length && !days[j + 1].has) j++;
        const a = md(days[i].date);
        const b = md(days[j].date);
        runs.push(a === b ? a : (a + '~' + b));
        i = j + 1;
      }
      return runs;
    }
    function md(dateStr) {
      return String(dateStr).slice(5); // 'YYYY-MM-DD' -> 'MM-DD'
    }
    function renderMemosHtml(items) {
      if (!items.length) return '';
      const cards = items.map(item => {
        const badges = [];
        if (item.pinned) badges.push('📌 置顶');
        if (item.done) badges.push('✅ 已完成');
        if (item.memo_date) badges.push('📅 ' + escapeHtml(item.memo_date));
        const meta = badges.length ? '<div class="meta">' + badges.join(' · ') + '</div>' : '';
        return '<article class="message">' +
          '<h2>' + escapeHtml(item.title || '（无标题）') + '</h2>' +
          meta +
          '<pre>' + renderMemoBody(item) + '</pre>' +
        '</article>';
      }).join('');
      return '<div class="message"><h2>备忘录</h2><p class="sub">对方共享的备忘（私密备忘不会同步）。</p></div>' + cards;
    }
    function renderMemoBody(item) {
      if (item.type === 'checklist') {
        try {
          const entries = JSON.parse(item.content || '[]');
          if (!entries.length) return '（空清单）';
          return entries.map(entry => (entry.d ? '☑ ' : '☐ ') + escapeHtml(entry.t || '')).join('\\n');
        } catch (error) {
          return escapeHtml(item.content || '');
        }
      }
      return escapeHtml(item.content || '');
    }
    function renderSummary(summary, viewerNickname) {
      const ownerLine = summary && summary.ownerNickname ? '对方昵称：' + escapeHtml(summary.ownerNickname) + '\\n' : '';
      const viewerLine = '我的昵称：' + escapeHtml(viewerNickname || '-') + '\\n';
      if (!summary || !summary.lastActivity) {
        return '<div class="message"><h2>状态摘要</h2><pre>' + ownerLine + viewerLine + '还没有活动记录。</pre></div>';
      }
      const isAlert = summary.status === 'inactive_alert';
      const statusText = isAlert ? '超过 72 小时没有新的首次解锁记录' : '最近有首次解锁记录';
      const inactiveText = summary.inactiveHours === null ? '未知' : summary.inactiveHours + ' 小时';
      const unread = summary.viewerUnreadMessages ?? summary.unreadMessages ?? 0;
      return '<div class="message' + (isAlert ? ' alert' : '') + '">' +
        '<h2>状态摘要</h2>' +
        '<pre>' +
        ownerLine +
        viewerLine +
        '状态：' + statusText + '\\n' +
        '最近记录的首次解锁：' + escapeHtml(summary.lastActivity.first_unlock_at) + '\\n' +
        '距这条记录：' + escapeHtml(inactiveText) + '\\n' +
        '我的未读消息：' + escapeHtml(unread) +
        '</pre>' +
      '</div>';
    }
    function renderMessagesHtml(items) {
      if (!items.length) return '<div class="empty">还没有消息。</div>';
      return items.map(item => {
        const alertClass = item.type === 'inactivity_alert' ? ' alert' : '';
        const readText = item.read_at ? '已读' : '未读';
        const badgeClass = item.read_at ? 'read' : 'unread';
        return '<article class="message' + alertClass + '">' +
          '<h2>' + escapeHtml(item.title) + '</h2>' +
          '<div class="meta">' + escapeHtml(item.sender_display_name) + ' · ' + escapeHtml(item.created_at) +
          ' · <span class="read-badge ' + badgeClass + '">' + readText + '</span></div>' +
          '<pre>' + escapeHtml(item.body) + '</pre>' +
        '</article>';
      }).join('');
    }
    async function markVisibleMessagesRead(ownerHandle, items) {
      const unread = items.filter(item => !item.read_at);
      await Promise.all(unread.map(item => fetch('/api/messages/' + encodeURIComponent(item.id) + '/read', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          syncId: ownerHandle,
          viewerId: saved.uid,
          viewerPassword: saved.password
        })
      }).catch(() => null)));
    }
    function escapeHtml(value) {
      return String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#039;');
    }
  </script>
</body>
</html>`;
}
