package fr.nivcoo.utilsz.core.messaging;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

final class InMemoryMessageBackend implements MessageBackend {

    private static final Map<String, List<Consumer<JsonObject>>> SUBSCRIBERS = new ConcurrentHashMap<>();

    private final String instanceId;
    private Consumer<Throwable> errorHandler = Throwable::printStackTrace;
    private volatile JsonObject lastPublished;

    InMemoryMessageBackend(String instanceId) {
        this.instanceId = instanceId;
    }

    static void reset() {
        SUBSCRIBERS.clear();
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public void start() {
    }

    @Override
    public void close() {
    }

    @Override
    public void subscribeRaw(String channel, Consumer<JsonObject> callback) {
        SUBSCRIBERS.computeIfAbsent(channel, ignored -> new CopyOnWriteArrayList<>()).add(callback);
    }

    @Override
    public void publish(String channel, JsonObject json) {
        lastPublished = json == null ? new JsonObject() : json.deepCopy();
        for (Consumer<JsonObject> subscriber : SUBSCRIBERS.getOrDefault(channel, List.of())) {
            try {
                subscriber.accept(lastPublished.deepCopy());
            } catch (Throwable throwable) {
                errorHandler.accept(throwable);
            }
        }
    }

    @Override
    public void onError(Consumer<Throwable> handler) {
        errorHandler = handler == null ? Throwable::printStackTrace : handler;
    }

    JsonObject lastPublished() {
        return lastPublished;
    }

    void replayLast(String channel) {
        JsonObject json = lastPublished;
        if (json == null) return;
        for (Consumer<JsonObject> subscriber : SUBSCRIBERS.getOrDefault(channel, List.of())) {
            subscriber.accept(json.deepCopy());
        }
    }
}
