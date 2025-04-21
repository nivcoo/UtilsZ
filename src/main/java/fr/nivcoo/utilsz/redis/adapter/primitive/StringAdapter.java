package fr.nivcoo.utilsz.redis.adapter.primitive;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.redis.RedisTypeAdapter;

public class StringAdapter implements RedisTypeAdapter<String> {

    @Override
    public JsonObject serialize(String value) {
        JsonObject json = new JsonObject();
        json.addProperty("value", value);
        return json;
    }

    @Override
    public String deserialize(JsonObject json) {
        return json.get("value").getAsString();
    }
}

