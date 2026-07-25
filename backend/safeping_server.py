from __future__ import annotations

import argparse
import json
import sqlite3
from datetime import date, datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from urllib.parse import parse_qs, urlparse


DEFAULT_DB_PATH = Path(__file__).with_name("safeping.db")


def connect(db_path: Path) -> sqlite3.Connection:
    connection = sqlite3.connect(str(db_path))
    connection.row_factory = sqlite3.Row
    return connection


def init_db(connection: sqlite3.Connection) -> None:
    connection.executescript(
        """
        CREATE TABLE IF NOT EXISTS users (
            device_id TEXT PRIMARY KEY,
            display_name TEXT NOT NULL,
            guardian_handle TEXT NOT NULL,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

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
        """
    )
    connection.commit()


def handle_unlock_event(connection: sqlite3.Connection, payload: Dict[str, Any]) -> Dict[str, Any]:
    required = ["deviceId", "displayName", "guardianHandle", "localDate", "firstUnlockAt"]
    missing = [key for key in required if not payload.get(key)]
    if missing:
        raise BadRequest(f"Missing fields: {', '.join(missing)}")

    device_id = str(payload["deviceId"])
    display_name = str(payload["displayName"])
    guardian_handle = str(payload["guardianHandle"])
    local_date = str(payload["localDate"])
    first_unlock_at = str(payload["firstUnlockAt"])
    parse_local_date(local_date)

    connection.execute(
        """
        INSERT INTO users(device_id, display_name, guardian_handle, updated_at)
        VALUES(?, ?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT(device_id) DO UPDATE SET
            display_name = excluded.display_name,
            guardian_handle = excluded.guardian_handle,
            updated_at = CURRENT_TIMESTAMP
        """,
        (device_id, display_name, guardian_handle),
    )
    connection.execute(
        """
        INSERT OR IGNORE INTO unlock_events(
            device_id, display_name, guardian_handle, local_date, first_unlock_at
        )
        VALUES(?, ?, ?, ?, ?)
        """,
        (device_id, display_name, guardian_handle, local_date, first_unlock_at),
    )

    report = maybe_create_weekly_report(connection, device_id, display_name, guardian_handle, local_date)
    connection.commit()
    return {"ok": True, "weeklyReportCreated": report is not None, "weeklyReport": report}


def handle_inactivity_alert(connection: sqlite3.Connection, payload: Dict[str, Any]) -> Dict[str, Any]:
    required = ["deviceId", "displayName", "guardianHandle", "inactiveHours"]
    missing = [key for key in required if payload.get(key) in (None, "")]
    if missing:
        raise BadRequest(f"Missing fields: {', '.join(missing)}")

    device_id = str(payload["deviceId"])
    display_name = str(payload["displayName"])
    guardian_handle = str(payload["guardianHandle"])
    last_activity_at = payload.get("lastActivityAt")
    inactive_hours = int(payload["inactiveHours"])

    existing = connection.execute(
        "SELECT id FROM inactivity_alerts WHERE device_id = ? AND last_activity_at IS ?",
        (device_id, last_activity_at),
    ).fetchone()
    if existing:
        connection.commit()
        return {"ok": True, "duplicate": True}

    title = "长时间无活动提醒"
    body = (
        f"{display_name} 已经超过 {inactive_hours} 小时没有活动记录。\n\n"
        "可能是手机关机、没网、App 被卸载、省电策略限制，或确实长时间没有使用手机。"
        "建议你直接联系确认情况。"
    )
    message_id = create_message(
        connection,
        recipient_handle=guardian_handle,
        sender_device_id=device_id,
        sender_display_name=display_name,
        message_type="inactivity_alert",
        title=title,
        body=body,
    )
    connection.execute(
        """
        INSERT INTO inactivity_alerts(
            device_id, guardian_handle, last_activity_at, inactive_hours, message_id
        )
        VALUES(?, ?, ?, ?, ?)
        """,
        (device_id, guardian_handle, last_activity_at, inactive_hours, message_id),
    )
    connection.commit()
    return {"ok": True, "messageId": message_id}


def maybe_create_weekly_report(
    connection: sqlite3.Connection,
    device_id: str,
    display_name: str,
    guardian_handle: str,
    local_date: str,
) -> Optional[Dict[str, Any]]:
    anchor = parse_local_date(local_date)
    week_start, week_end = eligible_report_week(anchor)
    if week_start is None or week_end is None:
        return None

    existing = connection.execute(
        "SELECT id FROM weekly_reports WHERE device_id = ? AND week_start = ?",
        (device_id, week_start.isoformat()),
    ).fetchone()
    if existing:
        return None

    events = connection.execute(
        """
        SELECT local_date, first_unlock_at
        FROM unlock_events
        WHERE device_id = ? AND local_date BETWEEN ? AND ?
        ORDER BY local_date ASC
        """,
        (device_id, week_start.isoformat(), week_end.isoformat()),
    ).fetchall()
    if not events:
        return None

    title = f"{display_name} 的本周平安记录"
    body = format_weekly_report_body(display_name, week_start, week_end, events)
    message_id = create_message(
        connection,
        recipient_handle=guardian_handle,
        sender_device_id=device_id,
        sender_display_name=display_name,
        message_type="weekly_report",
        title=title,
        body=body,
    )
    connection.execute(
        """
        INSERT INTO weekly_reports(device_id, guardian_handle, week_start, week_end, message_id)
        VALUES(?, ?, ?, ?, ?)
        """,
        (device_id, guardian_handle, week_start.isoformat(), week_end.isoformat(), message_id),
    )
    return {
        "messageId": message_id,
        "weekStart": week_start.isoformat(),
        "weekEnd": week_end.isoformat(),
        "eventCount": len(events),
    }


def eligible_report_week(anchor: date) -> Tuple[Optional[date], Optional[date]]:
    # Reports are generated after the weekly window is reachable by a recorder unlock.
    # Sunday unlocks report the current Monday-Sunday window. Monday-Saturday unlocks
    # report the previous completed week if it has not already been reported.
    if anchor.weekday() == 6:
        week_start = anchor - timedelta(days=6)
        return week_start, anchor
    previous_week_end = anchor - timedelta(days=anchor.weekday() + 1)
    previous_week_start = previous_week_end - timedelta(days=6)
    return previous_week_start, previous_week_end


def format_weekly_report_body(display_name: str, week_start: date, week_end: date, events: List[sqlite3.Row]) -> str:
    lines = [
        f"{display_name} 的平安记录",
        f"{week_start.isoformat()} 至 {week_end.isoformat()}",
        "",
    ]
    for event in events:
        lines.append(f"{event['local_date']}  {event['first_unlock_at']}")
    lines.extend(
        [
            "",
            "这表示记录端在这些日期有首次解锁活动。若记录缺失，可能是未解锁、关机、没网或系统限制。",
        ]
    )
    return "\n".join(lines)


def create_message(
    connection: sqlite3.Connection,
    recipient_handle: str,
    sender_device_id: str,
    sender_display_name: str,
    message_type: str,
    title: str,
    body: str,
) -> int:
    cursor = connection.execute(
        """
        INSERT INTO messages(
            recipient_handle, sender_device_id, sender_display_name, type, title, body
        )
        VALUES(?, ?, ?, ?, ?, ?)
        """,
        (recipient_handle, sender_device_id, sender_display_name, message_type, title, body),
    )
    return int(cursor.lastrowid)


def list_messages(connection: sqlite3.Connection, recipient_handle: str) -> List[Dict[str, Any]]:
    rows = connection.execute(
        """
        SELECT id, sender_display_name, type, title, body, read_at, created_at
        FROM messages
        WHERE recipient_handle = ?
        ORDER BY created_at DESC, id DESC
        LIMIT 100
        """,
        (recipient_handle,),
    ).fetchall()
    return [dict(row) for row in rows]


def mark_message_read(connection: sqlite3.Connection, message_id: int) -> Dict[str, Any]:
    connection.execute(
        "UPDATE messages SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP) WHERE id = ?",
        (message_id,),
    )
    connection.commit()
    return {"ok": True}


def parse_local_date(value: str) -> date:
    try:
        return date.fromisoformat(value)
    except ValueError as exc:
        raise BadRequest("localDate must use YYYY-MM-DD") from exc


class BadRequest(Exception):
    pass


class SafePingHandler(BaseHTTPRequestHandler):
    db_path = DEFAULT_DB_PATH

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/":
            self.write_html(200, inbox_page())
            return
        if parsed.path == "/health":
            self.write_json(200, {"ok": True})
            return
        if parsed.path == "/api/messages":
            query = parse_qs(parsed.query)
            recipient_handle = query.get("guardianHandle", [""])[0]
            if not recipient_handle:
                self.write_json(400, {"error": "guardianHandle is required"})
                return
            with connect(self.db_path) as connection:
                init_db(connection)
                self.write_json(200, {"messages": list_messages(connection, recipient_handle)})
            return
        self.write_json(404, {"error": "Not found"})

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        try:
            payload = self.read_json()
            with connect(self.db_path) as connection:
                init_db(connection)
                if parsed.path == "/api/unlock-events":
                    self.write_json(200, handle_unlock_event(connection, payload))
                    return
                if parsed.path == "/api/inactivity-alerts":
                    self.write_json(200, handle_inactivity_alert(connection, payload))
                    return
                if parsed.path.startswith("/api/messages/") and parsed.path.endswith("/read"):
                    message_id = int(parsed.path.split("/")[3])
                    self.write_json(200, mark_message_read(connection, message_id))
                    return
            self.write_json(404, {"error": "Not found"})
        except BadRequest as exc:
            self.write_json(400, {"error": str(exc)})
        except json.JSONDecodeError:
            self.write_json(400, {"error": "Invalid JSON"})
        except Exception as exc:
            self.write_json(500, {"error": str(exc)})

    def read_json(self) -> Dict[str, Any]:
        length = int(self.headers.get("Content-Length", "0"))
        data = self.rfile.read(length)
        if not data:
            return {}
        payload = json.loads(data.decode("utf-8"))
        if not isinstance(payload, dict):
            raise BadRequest("JSON body must be an object")
        return payload

    def write_json(self, status: int, payload: Dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def write_html(self, status: int, html: str) -> None:
        body = html.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args: Any) -> None:
        return


def run(host: str, port: int, db_path: Path) -> None:
    SafePingHandler.db_path = db_path
    with connect(db_path) as connection:
        init_db(connection)
    server = ThreadingHTTPServer((host, port), SafePingHandler)
    print(f"SafePing backend listening on http://{host}:{port}")
    print(f"SQLite database: {db_path}")
    server.serve_forever()


def inbox_page() -> str:
    return """<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>SafePing 收件箱</title>
  <style>
    :root {
      color-scheme: light;
      --bg: #f7f3ea;
      --panel: #ffffff;
      --text: #202421;
      --muted: #69736c;
      --line: #ddd5c6;
      --accent: #176d6a;
      --alert: #9b2c2c;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      background: var(--bg);
      color: var(--text);
      font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }
    main {
      width: min(720px, calc(100% - 32px));
      margin: 0 auto;
      padding: 28px 0 48px;
    }
    h1 {
      margin: 0;
      font-size: 30px;
      letter-spacing: 0;
    }
    .sub {
      margin: 8px 0 22px;
      color: var(--muted);
      line-height: 1.5;
    }
    .toolbar {
      display: grid;
      grid-template-columns: 1fr auto;
      gap: 10px;
      margin-bottom: 18px;
    }
    input, button {
      min-height: 46px;
      border: 1px solid var(--line);
      border-radius: 6px;
      font: inherit;
    }
    input {
      width: 100%;
      padding: 0 12px;
      background: var(--panel);
    }
    button {
      padding: 0 18px;
      background: var(--accent);
      color: white;
      border-color: var(--accent);
      cursor: pointer;
    }
    .status {
      min-height: 24px;
      color: var(--muted);
      margin-bottom: 12px;
    }
    .message {
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 8px;
      padding: 16px;
      margin: 12px 0;
    }
    .message.alert {
      border-color: #ddb1aa;
      background: #fff8f6;
    }
    .message h2 {
      margin: 0 0 6px;
      font-size: 18px;
      letter-spacing: 0;
    }
    .message.alert h2 { color: var(--alert); }
    .meta {
      color: var(--muted);
      font-size: 13px;
      margin-bottom: 12px;
    }
    pre {
      margin: 0;
      white-space: pre-wrap;
      word-break: break-word;
      font: inherit;
      line-height: 1.55;
    }
    .empty {
      border: 1px dashed var(--line);
      border-radius: 8px;
      padding: 24px;
      color: var(--muted);
      text-align: center;
    }
    @media (max-width: 520px) {
      main { width: min(100% - 24px, 720px); padding-top: 20px; }
      .toolbar { grid-template-columns: 1fr; }
      button { width: 100%; }
    }
  </style>
</head>
<body>
  <main>
    <h1>SafePing 收件箱</h1>
    <p class="sub">输入守护联系人 ID 查看收到的周报和异常提醒。MVP 阶段这个 ID 由记录端填写，例如 mom。</p>
    <div class="toolbar">
      <input id="handle" placeholder="守护联系人 ID，例如 mom" autocomplete="off">
      <button id="load">查看消息</button>
    </div>
    <div id="status" class="status"></div>
    <section id="messages"></section>
  </main>
  <script>
    const handleInput = document.getElementById('handle');
    const loadButton = document.getElementById('load');
    const status = document.getElementById('status');
    const messages = document.getElementById('messages');

    handleInput.value = localStorage.getItem('safePingHandle') || '';
    loadButton.addEventListener('click', loadMessages);
    handleInput.addEventListener('keydown', event => {
      if (event.key === 'Enter') loadMessages();
    });
    if (handleInput.value) loadMessages();

    async function loadMessages() {
      const handle = handleInput.value.trim();
      if (!handle) {
        status.textContent = '请输入守护联系人 ID。';
        return;
      }
      localStorage.setItem('safePingHandle', handle);
      status.textContent = '正在读取...';
      messages.innerHTML = '';
      try {
        const response = await fetch(`/api/messages?guardianHandle=${encodeURIComponent(handle)}`);
        const data = await response.json();
        if (!response.ok) throw new Error(data.error || '读取失败');
        renderMessages(data.messages || []);
        status.textContent = `共 ${data.messages.length} 条消息`;
      } catch (error) {
        status.textContent = error.message;
      }
    }

    function renderMessages(items) {
      if (!items.length) {
        messages.innerHTML = '<div class="empty">还没有消息。</div>';
        return;
      }
      messages.innerHTML = items.map(item => {
        const alertClass = item.type === 'inactivity_alert' ? ' alert' : '';
        return `<article class="message${alertClass}">
          <h2>${escapeHtml(item.title)}</h2>
          <div class="meta">${escapeHtml(item.sender_display_name)} · ${escapeHtml(item.created_at)}${item.read_at ? ' · 已读' : ''}</div>
          <pre>${escapeHtml(item.body)}</pre>
        </article>`;
      }).join('');
    }

    function escapeHtml(value) {
      return String(value)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
    }
  </script>
</body>
</html>"""


def main() -> None:
    parser = argparse.ArgumentParser(description="SafePing MVP backend")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default=8080, type=int)
    parser.add_argument("--db", default=str(DEFAULT_DB_PATH))
    args = parser.parse_args()
    run(args.host, args.port, Path(args.db))


if __name__ == "__main__":
    main()
