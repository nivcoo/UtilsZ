package fr.nivcoo.utilsz.core.messaging.crypto;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public final class AesGcmMessageCrypto implements MessageCrypto {

    public static final String ALGORITHM = "AES-256-GCM";

    private static final Gson GSON = new Gson();
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final byte[] key;
    private final String keyId;
    private final SecureRandom random = new SecureRandom();

    public AesGcmMessageCrypto(byte[] key, String keyId) {
        if (key == null || key.length != 32) {
            throw new IllegalArgumentException("AES-256-GCM requires a 32-byte key");
        }

        this.key = key.clone();
        this.keyId = keyId == null || keyId.isBlank() ? "default" : keyId;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public JsonObject encrypt(JsonObject clear, byte[] associatedData) throws Exception {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        if (associatedData != null && associatedData.length > 0) {
            cipher.updateAAD(associatedData);
        }

        byte[] encrypted = cipher.doFinal(GSON.toJson(clear).getBytes(StandardCharsets.UTF_8));

        JsonObject out = new JsonObject();
        out.addProperty("__encrypted", true);
        out.addProperty("alg", ALGORITHM);
        out.addProperty("kid", keyId);
        out.addProperty("nonce", Base64.getEncoder().encodeToString(nonce));
        out.addProperty("data", Base64.getEncoder().encodeToString(encrypted));
        return out;
    }

    @Override
    public JsonObject decrypt(JsonObject encrypted, byte[] associatedData) throws Exception {
        if (!isEncrypted(encrypted)) {
            return encrypted;
        }

        String alg = encrypted.has("alg") ? encrypted.get("alg").getAsString() : "";
        if (!ALGORITHM.equalsIgnoreCase(alg)) {
            throw new IllegalArgumentException("Unsupported message encryption algorithm: " + alg);
        }

        byte[] nonce = Base64.getDecoder().decode(encrypted.get("nonce").getAsString());
        byte[] data = Base64.getDecoder().decode(encrypted.get("data").getAsString());

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        if (associatedData != null && associatedData.length > 0) {
            cipher.updateAAD(associatedData);
        }

        byte[] clear = cipher.doFinal(data);
        return GSON.fromJson(new String(clear, StandardCharsets.UTF_8), JsonObject.class);
    }

    public static boolean isEncrypted(JsonObject payload) {
        return payload != null
                && payload.has("__encrypted")
                && payload.get("__encrypted").isJsonPrimitive()
                && payload.get("__encrypted").getAsBoolean();
    }
}
