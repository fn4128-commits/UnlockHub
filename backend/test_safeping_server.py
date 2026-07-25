import sqlite3
import tempfile
import unittest
from pathlib import Path

from backend.safeping_server import handle_inactivity_alert, handle_unlock_event, inbox_page, init_db, list_messages


class SafePingServerTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.db_path = Path(self.temp_dir.name) / "test.db"
        self.connection = sqlite3.connect(str(self.db_path))
        self.connection.row_factory = sqlite3.Row
        init_db(self.connection)

    def tearDown(self):
        self.connection.close()
        self.temp_dir.cleanup()

    def test_unlock_event_creates_weekly_report_on_sunday(self):
        for local_date in [
            "2026-06-01",
            "2026-06-02",
            "2026-06-03",
            "2026-06-04",
            "2026-06-05",
            "2026-06-06",
            "2026-06-07",
        ]:
            handle_unlock_event(
                self.connection,
                {
                    "deviceId": "device-1",
                    "displayName": "Alex",
                    "guardianHandle": "mom",
                    "localDate": local_date,
                    "firstUnlockAt": f"{local_date}T08:00:00+08:00",
                },
            )

        messages = list_messages(self.connection, "mom")
        self.assertEqual(1, len(messages))
        self.assertEqual("weekly_report", messages[0]["type"])
        self.assertIn("2026-06-01 至 2026-06-07", messages[0]["body"])

    def test_inactivity_alert_is_deduplicated_by_last_activity(self):
        payload = {
            "deviceId": "device-1",
            "displayName": "Alex",
            "guardianHandle": "mom",
            "lastActivityAt": "2026-06-01T08:00:00+08:00",
            "inactiveHours": 72,
        }
        first = handle_inactivity_alert(self.connection, payload)
        second = handle_inactivity_alert(self.connection, payload)

        messages = list_messages(self.connection, "mom")
        self.assertEqual(1, len(messages))
        self.assertTrue(first["ok"])
        self.assertTrue(second["duplicate"])

    def test_inbox_page_contains_client_loader(self):
        html = inbox_page()

        self.assertIn("SafePing 收件箱", html)
        self.assertIn("/api/messages?guardianHandle=", html)


if __name__ == "__main__":
    unittest.main()
