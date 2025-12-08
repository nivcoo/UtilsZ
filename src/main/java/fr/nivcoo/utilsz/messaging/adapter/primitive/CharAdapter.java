package fr.nivcoo.utilsz.messaging.adapter.primitive;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.messaging.BusTypeAdapter;

public class CharAdapter implements BusTypeAdapter<Character> {

    @Override
    public JsonObject serialize(Character value) {
        JsonObject json = new JsonObject();
        json.addProperty("value", value.toString());
        return json;
    }

    @Override
    public Character deserialize(JsonObject json) {
        String val = json.get("value").getAsString();
        return val.isEmpty() ? '\0' : val.charAt(0);
    }
}
