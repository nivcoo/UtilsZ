package fr.nivcoo.utilsz.core.messaging.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public final class MessageCryptoKeys {

    private static final char[] GENERATED_KEY_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private MessageCryptoKeys() {
    }

    public static byte[] decode(String configuredKey) {
        String key = configuredKey == null ? "" : configuredKey.trim();
        if (key.isEmpty()) {
            return new byte[0];
        }

        return key.getBytes(StandardCharsets.UTF_8);
    }

    public static String generate() {
        char[] key = new char[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = GENERATED_KEY_CHARS[RANDOM.nextInt(GENERATED_KEY_CHARS.length)];
        }
        return new String(key);
    }
}
