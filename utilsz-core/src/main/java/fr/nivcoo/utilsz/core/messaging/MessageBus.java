package fr.nivcoo.utilsz.core.messaging;

import com.google.gson.JsonObject;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public interface MessageBus {

    Duration DEFAULT_RPC_TIMEOUT = Duration.ofSeconds(10);

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

    default CompletableFuture<JsonObject> callRaw(String action, JsonObject payload, Duration timeout) {
        return callRaw(action, payload).orTimeout(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    default CompletableFuture<JsonObject> callRawTo(String targetInstanceId, String action, JsonObject payload) {
        throw new UnsupportedOperationException();
    }

    default CompletableFuture<JsonObject> callRawTo(String targetInstanceId, String action, JsonObject payload,
                                                      Duration timeout) {
        return callRawTo(targetInstanceId, action, payload)
                .orTimeout(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    default <Req, Res> CompletableFuture<Res> call(Req request, Class<Res> responseType) {
        throw new UnsupportedOperationException();
    }

    default <Req, Res> CompletableFuture<Res> call(Req request, Class<Res> responseType, Duration timeout) {
        return call(request, responseType).orTimeout(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    default <Req, Res> CompletableFuture<Res> callTo(String targetInstanceId, Req request, Class<Res> responseType) {
        throw new UnsupportedOperationException();
    }

    default <Req, Res> CompletableFuture<Res> callTo(String targetInstanceId, Req request,
                                                      Class<Res> responseType, Duration timeout) {
        return callTo(targetInstanceId, request, responseType)
                .orTimeout(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }
}
