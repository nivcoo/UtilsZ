package fr.nivcoo.utilsz.core.messaging.adapter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.core.messaging.BusTypeAdapter;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("rawtypes")
public class MapAdapter implements BusTypeAdapter<Map> {

    @Override
    public JsonObject serialize(Map value) {
        JsonObject json = new JsonObject();
        JsonObject data = new JsonObject();

        for (Object k : value.keySet()) {
            if (!(k instanceof String key)) continue;
            Object v = value.get(k);
            data.add(key, TypedJsonAdapter.serializeRuntime(v));
        }

        json.add("value", data);
        return json;
    }

    @Override
    public Map deserialize(JsonObject json) {
        Map<String, Object> result = new HashMap<>();
        JsonObject data = json.getAsJsonObject("value");
        if (data == null) return result;

        for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
            result.put(entry.getKey(), TypedJsonAdapter.deserializeRuntime(entry.getValue()));
        }

        return result;
    }
}
