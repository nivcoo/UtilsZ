package fr.nivcoo.utilsz.core.messaging.adapter;

import com.google.gson.*;
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
            array.add(new Gson().toJsonTree(element));
        }
        json.add("value", array);
        return json;
    }

    @Override
    public List deserialize(JsonObject json) {
        List<Object> list = new ArrayList<>();
        JsonArray array = json.getAsJsonArray("value");
        for (JsonElement element : array) {
            list.add(new Gson().fromJson(element, Object.class));
        }
        return list;
    }
}
