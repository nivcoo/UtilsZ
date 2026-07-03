package fr.nivcoo.utilsz.core.messaging.crypto;

import com.google.gson.JsonObject;

public interface MessageCrypto {

    boolean enabled();

    JsonObject encrypt(JsonObject clear, byte[] associatedData) throws Exception;

    JsonObject decrypt(JsonObject encrypted, byte[] associatedData) throws Exception;
}
