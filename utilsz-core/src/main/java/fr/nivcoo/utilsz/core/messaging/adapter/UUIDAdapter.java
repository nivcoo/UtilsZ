package fr.nivcoo.utilsz.core.messaging.adapter;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.core.messaging.BusTypeAdapter;

import java.util.UUID;

public class UUIDAdapter implements BusTypeAdapter<UUID> {
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
