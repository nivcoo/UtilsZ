package fr.nivcoo.utilsz.redis.adapter.primitive;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.redis.RedisTypeAdapter;

public class BooleanAdapter implements RedisTypeAdapter<Boolean> {
    @Override
    public JsonObject serialize(Boolean value) {
        JsonObject json = new JsonObject();
        json.addProperty("value", value);
        return json;
    }

    @Override
    public Boolean deserialize(JsonObject json) {
        return json.get("value").getAsBoolean();
    }
}
