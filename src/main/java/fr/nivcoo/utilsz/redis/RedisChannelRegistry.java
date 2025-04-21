package fr.nivcoo.utilsz.redis;

import com.google.gson.JsonObject;

import java.util.function.Consumer;

public class RedisChannelRegistry {

    private final RedisManager redisManager;
    private final String channel;

    public RedisChannelRegistry(RedisManager redisManager, String channel) {
        this(redisManager, channel, null);
    }

    public RedisChannelRegistry(RedisManager redisManager, String channel, Consumer<JsonObject> onRawMessage) {
        this.redisManager = redisManager;
        this.channel = channel;

        redisManager.subscribe(channel, (chan, json) -> {
            if (onRawMessage != null) {
                onRawMessage.accept(json);
            }
        });

        redisManager.start();
    }

    public <T extends RedisSerializable> void register(String action, RedisTypeAdapter<T> adapter, RedisHandler<T> handler) {
        redisManager.getDispatcher().register(channel, action, adapter, handler);
    }

    public <T extends RedisSerializable> void register(Class<T> clazz) {
        redisManager.getDispatcher().register(channel, clazz);
    }

    public void publish(RedisSerializable message) {
        JsonObject json = redisManager.getDispatcher().serialize(channel, message);
        redisManager.publish(channel, json);
    }

    public String getChannel() {
        return channel;
    }
}
