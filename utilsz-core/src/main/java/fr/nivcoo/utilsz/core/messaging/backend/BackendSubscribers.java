package fr.nivcoo.utilsz.core.messaging.backend;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

final class BackendSubscribers {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<JsonObject>>> subscribers = new ConcurrentHashMap<>();

    void add(String channel, Consumer<JsonObject> callback) {
        subscribers.computeIfAbsent(channel, ignored -> new CopyOnWriteArrayList<>()).add(callback);
    }

    Set<String> channels() {
        return subscribers.keySet();
    }

    void dispatch(String channel, JsonObject json, Consumer<Throwable> errorHandler) {
        List<Consumer<JsonObject>> callbacks = subscribers.getOrDefault(channel, new CopyOnWriteArrayList<>());
        for (Consumer<JsonObject> callback : callbacks) {
            try {
                callback.accept(json == null ? new JsonObject() : json.deepCopy());
            } catch (Throwable throwable) {
                errorHandler.accept(throwable);
            }
        }
    }
}
