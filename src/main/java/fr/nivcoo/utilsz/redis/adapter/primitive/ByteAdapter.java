package fr.nivcoo.utilsz.redis.adapter.primitive;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.redis.RedisTypeAdapter;

public class ByteAdapter implements RedisTypeAdapter<Byte> {

    @Override
    public JsonObject serialize(Byte value) {
        JsonObject json = new JsonObject();
        json.addProperty("value", value);
        return json;
    }

    @Override
    public Byte deserialize(JsonObject json) {
        return json.get("value").getAsByte();
    }
}
