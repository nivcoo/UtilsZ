package fr.nivcoo.utilsz.redis;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class RedisDispatcher {

    private final Map<String, RedisDeserializationEntry<?>> handlers = new HashMap<>();

    public <T extends RedisSerializable> void register(String action, Function<JsonObject, T> deserializer, RedisHandler<T> handler) {
        handlers.put(action, new RedisDeserializationEntry<>(deserializer, handler));
    }

    public void dispatch(String channel, String rawMessage) {
        JsonObject json = JsonParser.parseString(rawMessage).getAsJsonObject();
        dispatch(channel, json);
    }

    public void dispatch(String channel, JsonObject json) {
        String action = json.get("action").getAsString();
        RedisDeserializationEntry<?> entry = handlers.get(action);
        if (entry != null) {
            entry.handle(json);
        }
    }

    private record RedisDeserializationEntry<T extends RedisSerializable>(
            Function<JsonObject, T> deserializer,
            RedisHandler<T> handler
    ) {
        public void handle(JsonObject json) {
            T obj = deserializer.apply(json);
            handler.handle(obj);
        }
    }
}