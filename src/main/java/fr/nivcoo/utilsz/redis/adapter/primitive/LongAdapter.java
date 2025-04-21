package fr.nivcoo.utilsz.redis.adapter.primitive;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.redis.RedisTypeAdapter;

public class LongAdapter implements RedisTypeAdapter<Long> {
    @Override
    public JsonObject serialize(Long value) {
        JsonObject json = new JsonObject();
        json.addProperty("value", value);
        return json;
    }

    @Override
    public Long deserialize(JsonObject json) {
        return json.get("value").getAsLong();
    }
}
