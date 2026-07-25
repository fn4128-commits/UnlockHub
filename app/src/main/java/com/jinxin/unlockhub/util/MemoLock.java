package com.jinxin.unlockhub.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/** 私密备忘录 PIN：加盐 SHA-256 存储，仅本机校验。 */
public final class MemoLock {
    private static final String NAME = "memo_lock";
    private static final String KEY_SALT = "pin_salt";
    private static final String KEY_HASH = "pin_hash";

    private MemoLock() {
    }

    public static boolean hasPin(Context context) {
        return !prefs(context).getString(KEY_HASH, "").isEmpty();
    }

    public static void setPin(Context context, String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String saltHex = toHex(salt);
        prefs(context).edit()
                .putString(KEY_SALT, saltHex)
                .putString(KEY_HASH, hash(saltHex, pin))
                .apply();
    }

    public static boolean checkPin(Context context, String pin) {
        SharedPreferences prefs = prefs(context);
        String salt = prefs.getString(KEY_SALT, "");
        String expected = prefs.getString(KEY_HASH, "");
        if (expected.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(salt, pin).getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String hash(String saltHex, String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(saltHex.getBytes(StandardCharsets.UTF_8));
            digest.update(pin.getBytes(StandardCharsets.UTF_8));
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }
}
