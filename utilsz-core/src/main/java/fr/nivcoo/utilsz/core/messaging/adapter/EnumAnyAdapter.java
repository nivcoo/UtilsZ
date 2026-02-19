package fr.nivcoo.utilsz.core.messaging.adapter;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.core.messaging.BusTypeAdapter;

public final class EnumAnyAdapter implements BusTypeAdapter<Object> {
    private final Class<? extends Enum<?>> type;

    public EnumAnyAdapter(Class<? extends Enum<?>> type) { this.type = type; }

    @Override
    public JsonObject serialize(Object value) {
        JsonObject o = new JsonObject();
        String name = (value == null) ? null : ((Enum<?>) value).name();
        o.addProperty("name", name);
        return o;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Object deserialize(JsonObject json) {
        if (json == null || !json.has("name") || json.get("name").isJsonNull()) return null;
        String name = json.get("name").getAsString();
        return Enum.valueOf((Class) type, name);
    }
}
