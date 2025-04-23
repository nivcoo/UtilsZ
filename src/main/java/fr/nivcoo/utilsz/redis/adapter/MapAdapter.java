package fr.nivcoo.utilsz.redis.adapter;

import com.google.gson.*;
import fr.nivcoo.utilsz.redis.RedisTypeAdapter;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("rawtypes")
public class MapAdapter implements RedisTypeAdapter<Map> {

    private final Gson gson = new Gson();

    @Override
    public JsonObject serialize(Map value) {
        JsonObject json = new JsonObject();
        JsonObject data = new JsonObject();

        for (Object k : value.keySet()) {
            if (!(k instanceof String key)) continue;
            Object v = value.get(k);
            data.add(key, gson.toJsonTree(v));
        }

        json.add("value", data);
        return json;
    }

    @Override
    public Map deserialize(JsonObject json) {
        Map<String, Object> result = new HashMap<>();
        JsonObject data = json.getAsJsonObject("value");

        for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
            result.put(entry.getKey(), gson.fromJson(entry.getValue(), Object.class));
        }

        return result;
    }
}
