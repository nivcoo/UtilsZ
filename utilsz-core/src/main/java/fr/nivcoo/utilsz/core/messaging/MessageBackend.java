package fr.nivcoo.utilsz.core.messaging;

import com.google.gson.JsonObject;

import java.util.function.Consumer;

public interface MessageBackend {
    String getInstanceId();
    void start();
    void close();
    void subscribeRaw(String channel, Consumer<JsonObject> callback);
    void publish(String channel, JsonObject json);
}
