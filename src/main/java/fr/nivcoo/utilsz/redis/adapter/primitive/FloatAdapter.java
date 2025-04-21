package fr.nivcoo.utilsz.redis.adapter.primitive;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.redis.RedisTypeAdapter;

public class FloatAdapter implements RedisTypeAdapter<Float> {
    @Override
    public JsonObject serialize(Float value) {
        JsonObject json = new JsonObject();
        json.addProperty("value", value);
        return json;
    }

    @Override
    public Float deserialize(JsonObject json) {
        return json.get("value").getAsFloat();
    }
}
