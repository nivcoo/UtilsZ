package fr.nivcoo.utilsz.redis.bus;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.redis.RedisSerializable;

import java.util.concurrent.CompletableFuture;

public interface RedisChannelBus {
    void start();
    void close();
    void publish(RedisSerializable evt);
    void register(Class<?> clazz);

    default CompletableFuture<JsonObject> callRaw(String action, JsonObject payload) {
        throw new UnsupportedOperationException();
    }
}
