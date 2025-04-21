package fr.nivcoo.utilsz.redis.adapter.primitive;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.redis.RedisTypeAdapter;

public class CharAdapter implements RedisTypeAdapter<Character> {

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
