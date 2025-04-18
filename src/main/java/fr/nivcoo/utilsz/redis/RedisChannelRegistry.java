package fr.nivcoo.utilsz.redis;

import com.google.gson.JsonObject;
import java.util.function.Function;

public class RedisChannelRegistry {

    private final RedisManager redisManager;
    private final String channel;

    public RedisChannelRegistry(RedisManager redisManager, String channel) {
        this.redisManager = redisManager;
        this.channel = channel;

        redisManager.subscribe(channel, (chan, json) -> {
            String action = json.has("action") ? json.get("action").getAsString() : null;
            if (action != null) {
                redisManager.getDispatcher().dispatch(channel, json.toString());
            }
        });
    }

    public <T extends RedisSerializable> void register(String action, Function<JsonObject, T> deserializer, RedisHandler<T> handler) {
        redisManager.getDispatcher().register(action, deserializer, handler);
    }

    public void publish(RedisSerializable message) {
        redisManager.publish(channel, message.toJson());
    }

    public String getChannel() {
        return channel;
    }
}
