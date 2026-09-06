package fr.nivcoo.utilsz.core.messaging;

import com.google.gson.JsonObject;

import java.util.function.Consumer;

public interface MessageBackend {

    String getInstanceId();

    void start();

    void close();

    void subscribeRaw(String channel, Consumer<JsonObject> callback);

    default boolean ready() {
        return true;
    }

    void publish(String channel, JsonObject json);

    default void publishTo(String channel, String targetInstanceId, JsonObject json) {
        publish(channel, json);
    }

    void onError(Consumer<Throwable> handler);
}
