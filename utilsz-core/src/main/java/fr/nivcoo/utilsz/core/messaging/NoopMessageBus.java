package fr.nivcoo.utilsz.core.messaging;

import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

public final class NoopMessageBus implements MessageBus {

    private static final String DISABLED = "Messaging disabled";

    @Override
    public String instanceId() {
        return "noop";
    }

    @Override
    public void start() {
    }

    @Override
    public void close() {
    }

    @Override
    public void publish(BusMessage evt) {
    }

    @Override
    public void publishTo(String targetInstanceId, BusMessage evt) {
    }

    @Override
    public void register(Class<?> clazz) {
    }

    @Override
    public <T extends BusMessage> void register(Class<T> clazz, BusHandler<T> handler) {
    }

    @Override
    public CompletableFuture<JsonObject> callRaw(String a, JsonObject p) {
        return disabled();
    }

    @Override
    public CompletableFuture<JsonObject> callRawTo(String targetInstanceId, String action, JsonObject payload) {
        return disabled();
    }

    @Override
    public <Req, Res> CompletableFuture<Res> call(Req request, Class<Res> responseType) {
        return disabled();
    }

    @Override
    public <Req, Res> CompletableFuture<Res> callTo(String targetInstanceId, Req request, Class<Res> responseType) {
        return disabled();
    }

    private static <T> CompletableFuture<T> disabled() {
        return CompletableFuture.failedFuture(new IllegalStateException(DISABLED));
    }
}
