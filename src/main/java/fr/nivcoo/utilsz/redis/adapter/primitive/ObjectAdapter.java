package fr.nivcoo.utilsz.redis.adapter.primitive;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.redis.RedisTypeAdapter;

public class ObjectAdapter implements RedisTypeAdapter<Object> {

    private static final Gson gson = new Gson();

    @Override
    public JsonObject serialize(Object value) {
        JsonObject json = new JsonObject();
        json.add("value", gson.toJsonTree(value));
        json.addProperty("class", value.getClass().getName());
        return json;
    }

    @Override
    public Object deserialize(JsonObject json) {
        try {
            String className = json.get("class").getAsString();
            Class<?> clazz = Class.forName(className);
            return gson.fromJson(json.get("value"), clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize Object", e);
        }
    }
}
