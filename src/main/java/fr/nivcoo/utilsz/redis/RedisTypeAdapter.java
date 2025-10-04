package fr.nivcoo.utilsz.redis;

import com.google.gson.JsonObject;

public interface RedisTypeAdapter<T> {
    JsonObject serialize(T value);
    T deserialize(JsonObject json);
}
