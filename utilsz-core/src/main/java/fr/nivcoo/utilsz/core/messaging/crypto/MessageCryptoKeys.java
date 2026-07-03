package fr.nivcoo.utilsz.core.messaging.crypto;

import java.util.Base64;

public final class MessageCryptoKeys {

    private MessageCryptoKeys() {
    }

    public static byte[] decode(String configuredKey) {
        String key = configuredKey == null ? "" : configuredKey.trim();
        if (key.isEmpty()) {
            return new byte[0];
        }

        if (key.startsWith("base64:")) {
            return Base64.getDecoder().decode(key.substring("base64:".length()));
        }

        return Base64.getDecoder().decode(key);
    }
}
