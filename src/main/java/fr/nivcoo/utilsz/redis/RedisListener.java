package fr.nivcoo.utilsz.redis;

import com.google.gson.JsonObject;

@FunctionalInterface
public interface RedisListener {
    void onMessage(String channel, JsonObject message);
}