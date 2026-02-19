package fr.nivcoo.utilsz.core.messaging;

import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

public final class NoopMessageBus implements MessageBus {
    @Override
    public void start() {
    }

    @Override
    public void close() {
    }

    @Override
    public void publish(BusMessage evt) {
    }

    @Override
    public void register(Class<?> clazz) {
    }

    @Override
    public CompletableFuture<JsonObject> callRaw(String a, JsonObject p) {
        return CompletableFuture.failedFuture(new IllegalStateException("Messaging disabled"));
    }
}
