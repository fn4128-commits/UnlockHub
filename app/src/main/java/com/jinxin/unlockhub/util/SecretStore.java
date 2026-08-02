package com.jinxin.unlockhub.util;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 本地敏感值的静态加密（账号密码、私密备忘 PIN 哈希）。
 *
 * 密钥由 Android Keystore 生成并保管，**不可导出**：即使有人拿到 root 把整个数据目录拷走，
 * 拿到的也只是密文。要解开必须能在本应用进程里执行代码，门槛远高于"读一个 XML 文件"。
 *
 * 一切失败都退回明文（返回原值）。理由：这是纵深防御，不是功能依赖——绝不能因为某台设备的
 * Keystore 异常，就让用户连自己的账号都同步不了。
 */
public final class SecretStore {
    /** 密文前缀，用来和旧版本存的明文区分开，实现读时就地升级。 */
    public static final String PREFIX = "v1:";

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "unlockhub_local_secret_v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH = 12;

    private SecretStore() {
    }

    /** 是否已经是本类产出的密文。 */
    public static boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    /** 加密；失败则原样返回（调用方据此仍能正常工作，只是没有这层保护）。 */
    public static String encrypt(Context context, String plain) {
        if (plain == null || plain.isEmpty()) {
            return plain;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey());
            byte[] iv = cipher.getIV();
            byte[] body = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] joined = new byte[iv.length + body.length];
            System.arraycopy(iv, 0, joined, 0, iv.length);
            System.arraycopy(body, 0, joined, iv.length, body.length);
            return PREFIX + Base64.encodeToString(joined, Base64.NO_WRAP);
        } catch (Throwable ignored) {
            return plain;
        }
    }

    /** 解密；传入的若是旧版明文（无前缀）或解不开，原样返回。 */
    public static String decrypt(Context context, String stored) {
        if (!isEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] joined = Base64.decode(stored.substring(PREFIX.length()), Base64.NO_WRAP);
            if (joined.length <= IV_LENGTH) {
                return "";
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(joined, 0, iv, 0, IV_LENGTH);
            byte[] body = new byte[joined.length - IV_LENGTH];
            System.arraycopy(joined, IV_LENGTH, body, 0, body.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            // 密钥被系统清掉（如恢复出厂/换锁屏导致失效）时解不开：当作没存过，让用户重填。
            return "";
        }
    }

    private static synchronized SecretKey secretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // 不要求用户认证：解锁记录/同步要在锁屏状态下也能跑。
                .build());
        return generator.generateKey();
    }
}
