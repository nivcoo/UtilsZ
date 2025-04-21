package fr.nivcoo.utilsz.redis.adapter.primitive;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.redis.RedisTypeAdapter;

import java.util.UUID;

public class UUIDAdapter implements RedisTypeAdapter<UUID> {
    @Override
    public JsonObject serialize(UUID value) {
        JsonObject json = new JsonObject();
        json.addProperty("value", value.toString());
        return json;
    }

    @Override
    public UUID deserialize(JsonObject json) {
        return UUID.fromString(json.get("value").getAsString());
    }
}
