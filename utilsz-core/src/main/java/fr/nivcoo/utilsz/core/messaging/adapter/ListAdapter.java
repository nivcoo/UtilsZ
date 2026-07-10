package fr.nivcoo.utilsz.core.messaging.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.core.messaging.BusTypeAdapter;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("rawtypes")
public class ListAdapter implements BusTypeAdapter<List> {

    @Override
    public JsonObject serialize(List value) {
        JsonObject json = new JsonObject();
        JsonArray array = new JsonArray();

        for (Object element : value) {
            array.add(TypedJsonAdapter.serializeRuntime(element));
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
            list.add(TypedJsonAdapter.deserializeRuntime(element));
        }

        return list;
    }
}
