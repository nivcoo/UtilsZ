package fr.nivcoo.utilsz.redis;

import com.google.gson.JsonObject;

import java.util.function.Consumer;
import java.util.function.Function;

public class RedisChannelRegistry {

    private final RedisManager redisManager;
    private final String channel;

    public RedisChannelRegistry(RedisManager redisManager, String channel) {
        this(redisManager, channel, null);
    }

    public RedisChannelRegistry(RedisManager redisManager, String channel, Consumer<JsonObject> onMessage) {
        this.redisManager = redisManager;
        this.channel = channel;

        redisManager.subscribe(channel, (chan, json) -> {
            if (onMessage != null) {
                onMessage.accept(json);
            }
        });

        redisManager.start();
    }

    public <T extends RedisSerializable> void register(String action, Function<JsonObject, T> deserializer, RedisHandler<T> handler) {
        redisManager.getDispatcher().register(channel, action, deserializer, handler);
    }

    public void register(Class<? extends RedisSerializable> clazz) {
        redisManager.getDispatcher().register(channel, clazz);
    }

    public void publish(RedisSerializable message) {
        redisManager.publish(getChannel(), message.toJson());
    }

    public String getChannel() {
        return channel;
    }
}
