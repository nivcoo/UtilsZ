package fr.nivcoo.utilsz.core.messaging;

import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

public interface MessageBus {
    void start();
    void close();
    void publish(BusMessage evt);
    void register(Class<?> clazz);

    default CompletableFuture<JsonObject> callRaw(String action, JsonObject payload) {
        throw new UnsupportedOperationException();
    }
}
