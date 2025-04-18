package fr.nivcoo.utilsz.redis;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class RedisDispatcher {

    private final Map<String, RedisDeserializationEntry<?>> handlers = new HashMap<>();
    private final RedisManager redisManager;

    public RedisDispatcher(RedisManager redisManager) {
        this.redisManager = redisManager;
    }

    private String getKey(String channel, String action) {
        return channel + "#" + action;
    }

    public <T extends RedisSerializable> void register(String channel, String action, Function<JsonObject, T> deserializer, RedisHandler<T> handler) {
        String key = getKey(channel, action);
        handlers.put(key, new RedisDeserializationEntry<>(deserializer, handler));
    }

    public <T extends RedisSerializable> void register(String channel, Class<T> clazz) {
        RedisAction annotation = clazz.getAnnotation(RedisAction.class);
        if (annotation == null) {
            throw new RuntimeException("Missing @RedisAction annotation on class " + clazz.getName());
        }

        String action = annotation.value();
        Function<JsonObject, T> deserializer = json -> new Gson().fromJson(json, clazz);
        RedisHandler<T> handler = T::execute;

        register(channel, action, deserializer, handler);
    }

    public void dispatch(String channel, String rawMessage) {
        JsonObject json = JsonParser.parseString(rawMessage).getAsJsonObject();
        dispatch(channel, json);
    }

    public void dispatch(String channel, JsonObject json) {
        if (json.has("__sender")) {
            String sender = json.get("__sender").getAsString();
            if (sender.equals(redisManager.getInstanceId())) {
                return;
            }
        }

        if (!json.has("action"))
           return;


        String action = json.get("action").getAsString();
        String key = getKey(channel, action);
        RedisDeserializationEntry<?> entry = handlers.get(key);

        if (entry != null) {
            entry.handle(json);
        }
    }
}
