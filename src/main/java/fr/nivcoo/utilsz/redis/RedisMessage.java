package fr.nivcoo.utilsz.redis;

import com.google.gson.JsonObject;

import java.util.UUID;

public class RedisMessage {

    private final JsonObject json;

    public RedisMessage(String action) {
        this.json = new JsonObject();
        json.addProperty("action", action);
    }

    public RedisMessage add(String key, Object value) {
        if (value == null) return this;

        switch (value) {
            case String s -> json.addProperty(key, s);
            case UUID u -> json.addProperty(key, u.toString());
            case Integer i -> json.addProperty(key, i);
            case Boolean b -> json.addProperty(key, b);
            case Long l -> json.addProperty(key, l);
            case Double d -> json.addProperty(key, d);
            case Float f -> json.addProperty(key, f);
            default -> json.addProperty(key, value.toString());
        }

        return this;
    }

    public JsonObject toJson() {
        return json;
    }

    @Override
    public String toString() {
        return json.toString();
    }
}
