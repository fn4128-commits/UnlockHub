package com.jinxin.unlockhub.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 私密备忘录 PIN，仅本机校验。
 *
 * 两层防护，因为 PIN 通常只有 4 位、组合数极少：
 *  1. PBKDF2-HMAC-SHA256 加盐派生（不再用一次 SHA-256）——单次试错从"几乎免费"变成有实打实的开销；
 *  2. 派生结果再经 {@link SecretStore} 用 Keystore 密钥加密落盘——光把 XML 拷走根本拿不到可爆破的目标。
 *
 * 旧版本的加盐 SHA-256 记录仍能校验通过，通过之后就地升级成新格式。
 */
public final class MemoLock {
    private static final String NAME = "memo_lock";
    private static final String KEY_SALT = "pin_salt";
    private static final String KEY_HASH = "pin_hash";

    /** 新格式标记，写在派生结果前面，用来和旧的裸 SHA-256 记录区分。 */
    private static final String PBKDF2_PREFIX = "p2:";
    /** 校验发生在点「解锁」的那一下，太高会卡手；配合 Keystore 加密，这个强度足够。 */
    private static final int ITERATIONS = 50000;
    private static final int KEY_BITS = 256;

    private MemoLock() {
    }

    public static boolean hasPin(Context context) {
        return !readStored(context, KEY_HASH).isEmpty();
    }

    public static void setPin(Context context, String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String saltHex = toHex(salt);
        writeStored(context, saltHex, PBKDF2_PREFIX + pbkdf2(saltHex, pin));
    }

    public static boolean checkPin(Context context, String pin) {
        String salt = readStored(context, KEY_SALT);
        String expected = readStored(context, KEY_HASH);
        if (expected.isEmpty()) {
            return false;
        }
        if (expected.startsWith(PBKDF2_PREFIX)) {
            return constantTimeEquals(PBKDF2_PREFIX + pbkdf2(salt, pin), expected);
        }
        // 旧格式（加盐 SHA-256）：校验通过后立即升级到 PBKDF2 + Keystore。
        if (!constantTimeEquals(legacySha256(salt, pin), expected)) {
            return false;
        }
        setPin(context, pin);
        return true;
    }

    private static String pbkdf2(String saltHex, String pin) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    pin.toCharArray(),
                    saltHex.getBytes(StandardCharsets.UTF_8),
                    ITERATIONS,
                    KEY_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return toHex(factory.generateSecret(spec).getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String legacySha256(String saltHex, String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(saltHex.getBytes(StandardCharsets.UTF_8));
            digest.update(pin.getBytes(StandardCharsets.UTF_8));
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    /** 盐与派生结果都加密落盘。 */
    private static void writeStored(Context context, String saltHex, String hash) {
        prefs(context).edit()
                .putString(KEY_SALT, SecretStore.encrypt(context, saltHex))
                .putString(KEY_HASH, SecretStore.encrypt(context, hash))
                .apply();
    }

    /** 读出并解密；旧版本存的是明文，SecretStore 会原样返回。 */
    private static String readStored(Context context, String key) {
        String stored = prefs(context).getString(key, "");
        if (stored == null || stored.isEmpty()) {
            return "";
        }
        String plain = SecretStore.decrypt(context, stored);
        return plain == null ? "" : plain;
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
