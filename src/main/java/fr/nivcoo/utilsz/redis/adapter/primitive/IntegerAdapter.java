package fr.nivcoo.utilsz.redis.adapter.primitive;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.redis.RedisTypeAdapter;

public class IntegerAdapter implements RedisTypeAdapter<Integer> {

    @Override
    public JsonObject serialize(Integer value) {
        JsonObject json = new JsonObject();
        json.addProperty("value", value);
        return json;
    }

    @Override
    public Integer deserialize(JsonObject json) {
        return json.get("value").getAsInt();
    }
}
