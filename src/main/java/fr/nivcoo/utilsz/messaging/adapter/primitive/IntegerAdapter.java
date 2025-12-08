package fr.nivcoo.utilsz.messaging.adapter.primitive;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.messaging.BusTypeAdapter;

public class IntegerAdapter implements BusTypeAdapter<Integer> {

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
