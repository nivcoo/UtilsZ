package fr.nivcoo.utilsz.core.messaging.adapter.primitive;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.core.messaging.BusTypeAdapter;

public class FloatAdapter implements BusTypeAdapter<Float> {
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
