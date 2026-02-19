package fr.nivcoo.utilsz.core.messaging.adapter.primitive;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.core.messaging.BusTypeAdapter;

public class ShortAdapter implements BusTypeAdapter<Short> {

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
