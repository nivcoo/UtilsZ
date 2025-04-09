package fr.nivcoo.utilsz.redis;

import com.google.gson.JsonObject;

public interface RedisSerializable {
    String getAction();
    JsonObject toJson();
}
