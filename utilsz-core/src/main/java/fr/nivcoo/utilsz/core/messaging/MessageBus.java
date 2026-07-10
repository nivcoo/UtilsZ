package fr.nivcoo.utilsz.core.messaging;

import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

public interface MessageBus {

    String instanceId();

    void start();

    void close();

    void publish(BusMessage evt);

    void publishTo(String targetInstanceId, BusMessage evt);

    void register(Class<?> clazz);

    <T extends BusMessage> void register(Class<T> clazz, BusHandler<T> handler);

    default CompletableFuture<JsonObject> callRaw(String action, JsonObject payload) {
        throw new UnsupportedOperationException();
    }

    default CompletableFuture<JsonObject> callRawTo(String targetInstanceId, String action, JsonObject payload) {
        throw new UnsupportedOperationException();
    }

    default <Req, Res> CompletableFuture<Res> call(Req request, Class<Res> responseType) {
        throw new UnsupportedOperationException();
    }

    default <Req, Res> CompletableFuture<Res> callTo(String targetInstanceId, Req request, Class<Res> responseType) {
        throw new UnsupportedOperationException();
    }
}
