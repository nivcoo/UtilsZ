package fr.nivcoo.utilsz.redis;

import com.google.gson.JsonObject;


public interface RedisSerializable {
    JsonObject toJson();

    void execute();

    default String getAction() {
        RedisAction annotation = this.getClass().getAnnotation(RedisAction.class);

        return annotation != null ? annotation.value() : "unknown";
    }
}
