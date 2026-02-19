package fr.nivcoo.utilsz.core.messaging.adapter;

import com.google.gson.*;
import fr.nivcoo.utilsz.core.messaging.BusAdapterRegistry;
import fr.nivcoo.utilsz.core.messaging.BusTypeAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("rawtypes")
public class ListAdapter implements BusTypeAdapter<List> {

    private static final String TYPE = "__type";
    private static final Gson GSON = new Gson();

    @Override
    public JsonObject serialize(List value) {
        JsonObject json = new JsonObject();
        JsonArray array = new JsonArray();

        for (Object element : value) {
            array.add(encode(element));
        }

        json.add("value", array);
        return json;
    }

    @Override
    public List deserialize(JsonObject json) {
        List<Object> list = new ArrayList<>();
        JsonArray array = json.getAsJsonArray("value");
        if (array == null) return list;

        for (JsonElement element : array) {
            list.add(decode(element));
        }

        return list;
    }

    private static JsonElement encode(Object v) {
        if (v == null) return JsonNull.INSTANCE;

        BusTypeAdapter<Object> ad = BusAdapterRegistry.getAdapter(v.getClass());

        if (ad != null) {
            JsonObject o = ad.serialize(v);
            o.addProperty(TYPE, v.getClass().getName());
            return o;
        }

        return GSON.toJsonTree(v);
    }

    private static Object decode(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;

        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();

            if (obj.has(TYPE) && obj.get(TYPE).isJsonPrimitive()) {
                String cn = obj.get(TYPE).getAsString();
                try {
                    Class<?> clazz = Class.forName(cn);

                    BusTypeAdapter<Object> ad = BusAdapterRegistry.getAdapter(clazz);

                    if (ad != null) return ad.deserialize(obj);

                    obj.remove(TYPE);
                    return GSON.fromJson(obj, clazz);
                } catch (Throwable ignored) {
                }
            }

            return GSON.fromJson(obj, Object.class);
        }

        if (el.isJsonPrimitive()) {
            JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isString()) {
                String s = p.getAsString();
                UUID u = tryParseUuid(s);
                if (u != null) return u;
                return s;
            }
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) return p.getAsNumber();
        }

        return GSON.fromJson(el, Object.class);
    }

    private static UUID tryParseUuid(String s) {
        if (s == null) return null;
        if (s.length() != 36) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
