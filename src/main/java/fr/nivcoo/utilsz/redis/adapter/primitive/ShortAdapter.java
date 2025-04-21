package fr.nivcoo.utilsz.redis.adapter.primitive;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.redis.RedisTypeAdapter;

public class ShortAdapter implements RedisTypeAdapter<Short> {

    @Override
    public JsonObject serialize(Short value) {
        JsonObject json = new JsonObject();
        json.addProperty("value", value);
        return json;
    }

    @Override
    public Short deserialize(JsonObject json) {
        return json.get("value").getAsShort();
    }
}
