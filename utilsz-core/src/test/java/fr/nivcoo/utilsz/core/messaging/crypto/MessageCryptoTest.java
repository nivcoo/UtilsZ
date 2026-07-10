package fr.nivcoo.utilsz.core.messaging.crypto;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCryptoTest {

    @Test
    void generatedKeysAreValidRawAesKeys() {
        String generated = MessageCryptoKeys.generate();

        assertEquals(32, generated.length());
        assertEquals(32, MessageCryptoKeys.decode(generated).length);
    }

    @Test
    void aesGcmRoundTripRequiresSameAssociatedData() throws Exception {
        byte[] key = "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8);
        AesGcmMessageCrypto crypto = new AesGcmMessageCrypto(key, "test");
        JsonObject clear = new JsonObject();
        clear.addProperty("value", "secret");

        JsonObject encrypted = crypto.encrypt(clear, "aad-1".getBytes(StandardCharsets.UTF_8));

        assertTrue(AesGcmMessageCrypto.isEncrypted(encrypted));
        assertFalse(encrypted.has("value"));
        assertEquals("secret", crypto.decrypt(encrypted, "aad-1".getBytes(StandardCharsets.UTF_8))
                .get("value")
                .getAsString());
        assertThrows(Exception.class, () -> crypto.decrypt(encrypted, "aad-2".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aesGcmRejectsInvalidKeyLength() {
        assertThrows(IllegalArgumentException.class, () -> new AesGcmMessageCrypto(new byte[31], "bad"));
    }
}
