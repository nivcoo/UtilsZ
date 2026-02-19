package fr.nivcoo.utilsz.core.messaging.adapter.primitive;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.core.messaging.BusTypeAdapter;

public class BooleanAdapter implements BusTypeAdapter<Boolean> {
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
