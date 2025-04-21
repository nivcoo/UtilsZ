package fr.nivcoo.utilsz.redis;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;

public class RedisDispatcher {

    private final Map<String, RedisSerializationEntry<?>> handlers = new HashMap<>();
    private final RedisManager redisManager;

    public RedisDispatcher(RedisManager redisManager) {
        this.redisManager = redisManager;
    }

    private String getKey(String channel, String action) {
        return channel + "#" + action;
    }

    public <T extends RedisSerializable> void register(String channel, String action, RedisTypeAdapter<T> adapter, RedisHandler<T> handler) {
        String key = getKey(channel, action);
        handlers.put(key, new RedisSerializationEntry<>(adapter, handler));
    }

    public <T extends RedisSerializable> void register(String channel, Class<T> clazz) {
        RedisAction annotation = clazz.getAnnotation(RedisAction.class);
        if (annotation == null) {
            throw new RuntimeException("Missing @RedisAction annotation on class " + clazz.getName());
        }

        String action = annotation.value();
        RedisTypeAdapter<T> adapter = RedisAdapterRegistry.getAdapter(clazz);
        if (adapter == null) {
            throw new RuntimeException("No RedisTypeAdapter registered for class " + clazz.getName());
        }

        RedisHandler<T> handler = T::execute;
        register(channel, action, adapter, handler);
    }

    public void dispatch(String channel, String rawMessage) {
        JsonObject json = JsonParser.parseString(rawMessage).getAsJsonObject();
        dispatch(channel, json);
    }

    public void dispatch(String channel, JsonObject json) {
        if (json.has("__sender") && json.get("__sender").getAsString().equals(redisManager.getInstanceId())) return;
        if (!json.has("action")) return;

        String action = json.get("action").getAsString();
        String key = getKey(channel, action);
        RedisSerializationEntry<?> entry = handlers.get(key);

        if (entry != null) {
            entry.handle(json);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends RedisSerializable> JsonObject serialize(String channel, T object) {
        String action = object.getAction();
        String key = getKey(channel, action);
        RedisSerializationEntry<T> entry = (RedisSerializationEntry<T>) handlers.get(key);

        if (entry == null) {
            throw new IllegalStateException("No handler registered for " + key);
        }

        JsonObject json = entry.serialize(object);
        json.addProperty("action", action);
        return json;
    }
}
