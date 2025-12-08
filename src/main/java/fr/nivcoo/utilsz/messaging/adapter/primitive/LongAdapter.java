package fr.nivcoo.utilsz.messaging.adapter.primitive;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.messaging.BusTypeAdapter;

public class LongAdapter implements BusTypeAdapter<Long> {
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
