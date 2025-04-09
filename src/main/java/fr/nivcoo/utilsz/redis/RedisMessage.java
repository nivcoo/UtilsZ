package fr.nivcoo.utilsz.redis;

import com.google.gson.JsonObject;

import java.util.UUID;

public class RedisMessage {

    private final JsonObject json;

    public RedisMessage(String action) {
        this.json = new JsonObject();
        this.json.addProperty("action", action);
    }

    public RedisMessage add(String key, String value) {
        json.addProperty(key, value);
        return this;
    }

    public RedisMessage add(String key, UUID value) {
        json.addProperty(key, value.toString());
        return this;
    }

    public RedisMessage add(String key, int value) {
        json.addProperty(key, value);
        return this;
    }

    public RedisMessage add(String key, boolean value) {
        json.addProperty(key, value);
        return this;
    }

    public JsonObject toJson() {
        return json;
    }

    public String toString() {
        return json.toString();
    }
}