package fr.nivcoo.utilsz.core.messaging.adapter.primitive;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.core.messaging.BusTypeAdapter;

public class ByteAdapter implements BusTypeAdapter<Byte> {

    @Override
    public JsonObject serialize(Byte value) {
        JsonObject json = new JsonObject();
        json.addProperty("value", value);
        return json;
    }

    @Override
    public Byte deserialize(JsonObject json) {
        return json.get("value").getAsByte();
    }
}
