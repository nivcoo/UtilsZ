package fr.nivcoo.utilsz.messaging.adapter.primitive;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.messaging.BusTypeAdapter;

public class DoubleAdapter implements BusTypeAdapter<Double> {
    @Override
    public JsonObject serialize(Double value) {
        JsonObject json = new JsonObject();
        json.addProperty("value", value);
        return json;
    }

    @Override
    public Double deserialize(JsonObject json) {
        return json.get("value").getAsDouble();
    }
}
