package fr.nivcoo.utilsz.redis.adapter.primitive;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.redis.RedisTypeAdapter;

public class DoubleAdapter implements RedisTypeAdapter<Double> {
    @Override
    public JsonObject serialize(Double value) {
        JsonObject json = new JsonObject();
        json.addProperty("value", value);
        return json;
    }

    @Override
    public Double deserialize(JsonObject json) {
        return json.get("value").getAsDouble();
    }
}
