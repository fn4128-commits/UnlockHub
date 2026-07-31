package com.jinxin.unlockhub.network;

import android.content.Context;
import android.net.Uri;

import com.jinxin.unlockhub.data.UnlockEvent;
import com.jinxin.unlockhub.sync.SyncSchedule;
import com.jinxin.unlockhub.util.Prefs;
import com.jinxin.unlockhub.util.TimeFormat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ApiClient {
    private final Context context;

    public ApiClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean hasBackend() {
        return !Prefs.backendUrl(context).isEmpty();
    }

    public SyncResult sendUnlockEvent(UnlockEvent event) throws IOException {
        requireAccount();
        String body = "{" +
                json("deviceId", Prefs.deviceId(context)) + "," +
                json("publicId", Prefs.publicId(context)) + "," +
                json("displayName", Prefs.displayName(context)) + "," +
                json("guardianHandle", Prefs.guardianHandle(context)) + "," +
                json("receiverAccessKey", Prefs.receiverAccessKey(context)) + "," +
                json("localDate", event.localDate) + "," +
                json("firstUnlockAt", TimeFormat.isoOffset(event.firstUnlockAt)) + "," +
                json("syncMode", Prefs.syncMode(context)) + "," +
                jsonNumber("syncWeekdaysMask", Prefs.syncWeekdaysMask(context)) + "," +
                json("syncAnchorDate", SyncSchedule.syncAnchorDate(context)) + "," +
                jsonNumber("syncIntervalDays", SyncSchedule.syncIntervalDays(context)) +
                "}";
        String response = postForBody("/api/unlock-events", body);
        return SyncResult.fromJson(response);
    }

    public void sendInactivityAlert(long lastActivityAt) throws IOException {
        requireAccount();
        String body = "{" +
                json("deviceId", Prefs.deviceId(context)) + "," +
                json("publicId", Prefs.publicId(context)) + "," +
                json("displayName", Prefs.displayName(context)) + "," +
                json("guardianHandle", Prefs.guardianHandle(context)) + "," +
                json("receiverAccessKey", Prefs.receiverAccessKey(context)) + "," +
                json("lastActivityAt", lastActivityAt > 0L ? TimeFormat.isoOffset(lastActivityAt) : null) + "," +
                "\"inactiveHours\":72" +
                "}";
        post("/api/inactivity-alerts", body);
    }

    /** 全量上传非私密备忘（memosJsonArray 为 JSON 数组字符串）。 */

    public void sendTestWeeklyReport() throws IOException {
        requireAccount();
        String body = "{" +
                json("deviceId", Prefs.deviceId(context)) + "," +
                json("publicId", Prefs.publicId(context)) + "," +
                json("displayName", Prefs.displayName(context)) + "," +
                json("guardianHandle", Prefs.guardianHandle(context)) + "," +
                json("receiverAccessKey", Prefs.receiverAccessKey(context)) +
                "}";
        post("/api/test-weekly-report", body);
    }

    public Account registerAccount(String nickname, String password, String email) throws IOException {
        String body = "{" +
                json("nickname", nickname) + "," +
                json("password", password) + "," +
                json("email", email) + "," +
                json("role", "owner") +
                "}";
        return accountFromJson(context, postForBody("/api/register", body));
    }

    public Account loginAccount(String nickname, String password) throws IOException {
        return loginAccount(nickname, password, "");
    }

    /** email 仅在「同名同密码」冲突时需要，用于区分账号；平时传空串。 */
    public Account loginAccount(String nickname, String password, String email) throws IOException {
        String body = "{" +
                json("nickname", nickname) + "," +
                json("password", password) + "," +
                json("email", email) +
                "}";
        return accountFromJson(context, postForBody("/api/login", body));
    }

    /** 为老账号补填邮箱（仅在服务端返回 needsEmail 时需要）。 */
    public void setEmail(String publicId, String password, String email) throws IOException {
        String body = "{" +
                json("publicId", publicId) + "," +
                json("password", password) + "," +
                json("email", email) +
                "}";
        postForBody("/api/set-email", body);
    }

    public void changePassword(String publicId, String currentPassword, String newPassword) throws IOException {
        String body = "{" +
                json("publicId", publicId) + "," +
                json("currentPassword", currentPassword) + "," +
                json("newPassword", newPassword) +
                "}";
        post("/api/change-password", body);
    }

    public String listViewersJson() throws IOException {
        requireAccount();
        // 访问密钥走请求头，不放 URL（避免进日志/历史）。
        return getForBody("/api/viewers?syncId=" + encode(Prefs.publicId(context)),
                Prefs.receiverAccessKey(context));
    }

    public void addViewer(String nickname) throws IOException {
        requireAccount();
        String body = "{" +
                json("syncId", Prefs.publicId(context)) + "," +
                json("accessPassword", Prefs.receiverAccessKey(context)) + "," +
                json("nickname", nickname) +
                "}";
        post("/api/viewers", body);
    }

    public void updateViewer(long viewerId, String nickname) throws IOException {
        requireAccount();
        String body = "{" +
                json("syncId", Prefs.publicId(context)) + "," +
                json("accessPassword", Prefs.receiverAccessKey(context)) + "," +
                json("nickname", nickname) +
                "}";
        put("/api/viewers/" + viewerId, body);
    }

    public void deleteViewer(long viewerId) throws IOException {
        requireAccount();
        delete("/api/viewers/" + viewerId + "?syncId=" + encode(Prefs.publicId(context)),
                Prefs.receiverAccessKey(context));
    }

    public String listOwnerMessagesJson() throws IOException {
        requireAccount();
        return getForBody("/api/messages?ownerView=1&syncId=" + encode(Prefs.publicId(context)),
                Prefs.receiverAccessKey(context));
    }

    public void checkHealth() throws IOException {
        String baseUrl = Prefs.backendUrl(context);
        if (baseUrl.isEmpty()) {
            throw new IOException("Backend URL is not configured.");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/health").openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        int code = connection.getResponseCode();
        try (InputStream ignored = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream()) {
            if (code < 200 || code >= 300) {
                throw new IOException("Backend returned HTTP " + code);
            }
        }
    }

    private void post(String path, String body) throws IOException {
        postForBody(path, body);
    }

    private String getForBody(String path) throws IOException {
        return getForBody(path, null);
    }

    private String getForBody(String path, String accessKey) throws IOException {
        String baseUrl = Prefs.backendUrl(context);
        if (baseUrl.isEmpty()) {
            throw new IOException("Backend URL is not configured.");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod("GET");
        if (accessKey != null && !accessKey.isEmpty()) {
            connection.setRequestProperty("x-access-key", accessKey);
        }
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        int code = connection.getResponseCode();
        String responseBody;
        try (InputStream input = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream()) {
            responseBody = input == null ? "" : readBody(input);
        }
        if (code < 200 || code >= 300) {
            String error = extractJsonString(responseBody, "error");
            throw new IOException(error == null || error.isEmpty() ? "Backend returned HTTP " + code : error);
        }
        return responseBody;
    }

    private void put(String path, String body) throws IOException {
        String baseUrl = Prefs.backendUrl(context);
        if (baseUrl.isEmpty()) {
            throw new IOException("Backend URL is not configured.");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod("PUT");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(payload.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload);
        }
        int code = connection.getResponseCode();
        try (InputStream input = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream()) {
            if (input != null) {
                readBody(input);
            }
        }
        if (code < 200 || code >= 300) {
            throw new IOException("Backend returned HTTP " + code);
        }
    }

    private void delete(String path, String accessKey) throws IOException {
        String baseUrl = Prefs.backendUrl(context);
        if (baseUrl.isEmpty()) {
            throw new IOException("Backend URL is not configured.");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod("DELETE");
        if (accessKey != null && !accessKey.isEmpty()) {
            connection.setRequestProperty("x-access-key", accessKey);
        }
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        int code = connection.getResponseCode();
        try (InputStream input = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream()) {
            if (input != null) {
                readBody(input);
            }
        }
        if (code < 200 || code >= 300) {
            throw new IOException("Backend returned HTTP " + code);
        }
    }

    private static String encode(String value) {
        return Uri.encode(value == null ? "" : value);
    }

    private String postForBody(String path, String body) throws IOException {
        String baseUrl = Prefs.backendUrl(context);
        if (baseUrl.isEmpty()) {
            throw new IOException("Backend URL is not configured.");
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(payload.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload);
        }

        int code = connection.getResponseCode();
        String responseBody;
        try (InputStream input = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream()) {
            responseBody = input == null ? "" : readBody(input);
        }
        if (code < 200 || code >= 300) {
            String error = extractJsonString(responseBody, "error");
            throw new IOException(error == null || error.isEmpty() ? "Backend returned HTTP " + code : error);
        }
        return responseBody;
    }

    private static String readBody(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private void requireAccount() throws IOException {
        if (!Prefs.isAccountBound(context)) {
            throw new IOException(context.getString(com.jinxin.unlockhub.R.string.api_need_login));
        }
    }

    private static String json(String key, String value) {
        return "\"" + escape(key) + "\":" + (value == null ? "null" : "\"" + escape(value) + "\"");
    }

    private static String jsonNumber(String key, int value) {
        return "\"" + escape(key) + "\":" + value;
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static Account accountFromJson(Context context, String json) throws IOException {
        String publicId = extractJsonString(json, "publicId");
        String nickname = extractJsonString(json, "nickname");
        if (publicId == null || publicId.isEmpty()) {
            throw new IOException(context.getString(com.jinxin.unlockhub.R.string.api_no_uid));
        }
        boolean needsEmail = json.contains("\"needsEmail\":true");
        return new Account(publicId, nickname == null ? "" : nickname, needsEmail);
    }

    private static String extractJsonString(String json, String key) {
        String needle = "\"" + key + "\"";
        int keyIndex = json.indexOf(needle);
        if (keyIndex < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyIndex + needle.length());
        if (colon < 0) {
            return null;
        }
        int start = json.indexOf('"', colon + 1);
        if (start < 0) {
            return null;
        }
        StringBuilder value = new StringBuilder();
        boolean escaping = false;
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaping) {
                if (c == 'n') {
                    value.append('\n');
                } else if (c == 'r') {
                    value.append('\r');
                } else {
                    value.append(c);
                }
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else if (c == '"') {
                return value.toString();
            } else {
                value.append(c);
            }
        }
        return null;
    }

    public static final class Account {
        public final String publicId;
        public final String nickname;
        /** 服务端标记：该账号尚未设置邮箱（迁移前注册的老账号），应提示补填。 */
        public final boolean needsEmail;

        private Account(String publicId, String nickname, boolean needsEmail) {
            this.publicId = publicId;
            this.nickname = nickname;
            this.needsEmail = needsEmail;
        }
    }

    public static final class SyncResult {
        public final boolean syncReportCreated;
        public final String dueDate;
        public final String periodStart;
        public final String periodEnd;

        private SyncResult(boolean syncReportCreated, String dueDate, String periodStart, String periodEnd) {
            this.syncReportCreated = syncReportCreated;
            this.dueDate = dueDate;
            this.periodStart = periodStart;
            this.periodEnd = periodEnd;
        }

        public static SyncResult empty() {
            return new SyncResult(false, null, null, null);
        }

        public static SyncResult fromJson(String json) {
            if (json == null || json.isEmpty()) {
                return empty();
            }
            boolean created = json.contains("\"syncReportCreated\":true")
                    || json.contains("\"weeklyReportCreated\":true");
            String dueDate = extractJsonString(json, "dueDate");
            if (dueDate == null) {
                dueDate = extractJsonString(json, "weekStart");
            }
            String periodStart = extractJsonString(json, "periodStart");
            if (periodStart == null) {
                periodStart = extractJsonString(json, "weekStart");
            }
            String periodEnd = extractJsonString(json, "periodEnd");
            if (periodEnd == null) {
                periodEnd = extractJsonString(json, "weekEnd");
            }
            return new SyncResult(created, dueDate, periodStart, periodEnd);
        }
    }
}
