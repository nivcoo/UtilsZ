package fr.nivcoo.utilsz.core.messaging.crypto;

import com.google.gson.JsonObject;

public final class NoopMessageCrypto implements MessageCrypto {

    public static final NoopMessageCrypto INSTANCE = new NoopMessageCrypto();

    private NoopMessageCrypto() {
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public JsonObject encrypt(JsonObject clear, byte[] associatedData) {
        return clear;
    }

    @Override
    public JsonObject decrypt(JsonObject encrypted, byte[] associatedData) {
        return encrypted;
    }
}
