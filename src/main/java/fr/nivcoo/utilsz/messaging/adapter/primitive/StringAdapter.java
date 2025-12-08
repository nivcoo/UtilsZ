package fr.nivcoo.utilsz.messaging.adapter.primitive;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.messaging.BusTypeAdapter;

public class StringAdapter implements BusTypeAdapter<String> {

    @Override
    public JsonObject serialize(String value) {
        JsonObject json = new JsonObject();
        json.addProperty("value", value);
        return json;
    }

    @Override
    public String deserialize(JsonObject json) {
        return json.get("value").getAsString();
    }
}

