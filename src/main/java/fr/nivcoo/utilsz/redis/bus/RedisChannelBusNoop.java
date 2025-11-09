package fr.nivcoo.utilsz.redis.bus;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.redis.RedisSerializable;

import java.util.concurrent.CompletableFuture;

public final class RedisChannelBusNoop implements RedisChannelBus {
    @Override public void start() {}
    @Override public void close() {}
    @Override public void publish(RedisSerializable evt) {}
    @Override public void register(Class<?> clazz) {}
    @Override public CompletableFuture<JsonObject> callRaw(String a, JsonObject p) {
        return CompletableFuture.failedFuture(
                new IllegalStateException("Redis disabled"));
    }
}
