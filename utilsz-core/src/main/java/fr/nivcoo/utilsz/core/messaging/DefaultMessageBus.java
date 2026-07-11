package fr.nivcoo.utilsz.core.messaging;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.core.messaging.crypto.AesGcmMessageCrypto;
import fr.nivcoo.utilsz.core.messaging.crypto.MessageCrypto;
import fr.nivcoo.utilsz.core.messaging.crypto.NoopMessageCrypto;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class DefaultMessageBus implements MessageBus {

    private static final class Envelope {
        String kind;
        String action;
        String cid;
        String mid;
        JsonObject payload;
        String error;
        String sender;
        String target;
        long ts;
    }

    private record EventEntry<T extends BusMessage>(BusTypeAdapter<T> adapter, BusHandler<T> handler) {}
    private record RpcEntry(BusTypeAdapter<Object> reqAdapter) {}
    private record RequestKey(String sender, String correlationId) {}

    private static final class RequestExecution {
        private final CompletableFuture<Envelope> response = new CompletableFuture<>();
        private volatile long completedAt;

        private void complete(Envelope envelope) {
            if (response.complete(envelope)) {
                completedAt = System.currentTimeMillis();
            }
        }
    }

    private final MessageBackend backend;
    private final String channel;
    private final Consumer<Runnable> mainThreadExecutor;
    private final Logger logger;
    private final MessageCrypto crypto;
    private final Duration defaultRpcTimeout;
    private final ScheduledThreadPoolExecutor rpcTimeouts;

    private final ConcurrentMap<String, EventEntry<?>> eventHandlers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RpcEntry> rpcHandlers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> seen = new ConcurrentHashMap<>();
    private final ConcurrentMap<RequestKey, RequestExecution> requestExecutions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> selfReceive = new ConcurrentHashMap<>();

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean subscribed = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile long lastGcAt = 0L;

    public DefaultMessageBus(MessageBackend backend,
                             String channel,
                             Consumer<Runnable> mainThreadExecutor,
                             Logger logger) {

        this(backend, channel, mainThreadExecutor, logger, NoopMessageCrypto.INSTANCE, DEFAULT_RPC_TIMEOUT);
    }

    public DefaultMessageBus(MessageBackend backend,
                             String channel,
                             Consumer<Runnable> mainThreadExecutor,
                             Logger logger,
                             MessageCrypto crypto) {

        this(backend, channel, mainThreadExecutor, logger, crypto, DEFAULT_RPC_TIMEOUT);
    }

    public DefaultMessageBus(MessageBackend backend,
                             String channel,
                             Consumer<Runnable> mainThreadExecutor,
                             Logger logger,
                             MessageCrypto crypto,
                             Duration defaultRpcTimeout) {

        this.backend = backend;
        this.channel = channel;
        this.mainThreadExecutor = mainThreadExecutor;
        this.logger = logger;
        this.crypto = crypto == null ? NoopMessageCrypto.INSTANCE : crypto;
        this.defaultRpcTimeout = requireTimeout(defaultRpcTimeout);
        this.rpcTimeouts = createTimeoutExecutor();

        BusAdapterRegistry.registerBuiltins();

        backend.onError(t ->
                logger.warn("[MessageBus] Backend error: {}", t.getMessage())
        );
    }

    @Override
    public String instanceId() {
        return backend.getInstanceId();
    }

    @Override
    public synchronized void start() {
        if (closed.get()) {
            throw new IllegalStateException("Message bus is closed");
        }
        if (started.get()) return;

        if (!subscribed.get()) {
            backend.subscribeRaw(channel, this::onIncoming);
            subscribed.set(true);
        }

        started.set(true);
        try {
            backend.start();
        } catch (RuntimeException | Error error) {
            started.set(false);
            throw error;
        }
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) return;

        started.set(false);
        pending.forEach((cid, fut) ->
                fut.completeExceptionally(new CancellationException("Bus closed"))
        );
        pending.clear();
        requestExecutions.forEach((key, execution) -> execution.complete(null));
        requestExecutions.clear();
        rpcTimeouts.shutdownNow();
        backend.close();
    }

    @Override
    public void publish(BusMessage evt) {
        publishEvent(null, evt);
    }

    @Override
    public void publishTo(String targetInstanceId, BusMessage evt) {
        publishEvent(targetInstanceId, evt);
    }

    @Override
    public CompletableFuture<JsonObject> callRaw(String action, JsonObject payload) {
        return callRawTo(null, action, payload, defaultRpcTimeout);
    }

    @Override
    public CompletableFuture<JsonObject> callRaw(String action, JsonObject payload, Duration timeout) {
        return callRawTo(null, action, payload, timeout);
    }

    @Override
    public CompletableFuture<JsonObject> callRawTo(String targetInstanceId, String action, JsonObject payload) {
        return callRawTo(targetInstanceId, action, payload, defaultRpcTimeout);
    }

    @Override
    public CompletableFuture<JsonObject> callRawTo(String targetInstanceId, String action, JsonObject payload,
                                                    Duration timeout) {
        Duration resolvedTimeout = requireTimeout(timeout);
        if (!isRunning()) {
            return failedFuture(new IllegalStateException("Message bus is not started"));
        }

        String cid = UUID.randomUUID().toString();

        Envelope env = new Envelope();
        env.kind = "req";
        env.action = action;
        env.cid = cid;
        env.target = targetInstanceId;
        env.payload = payload;

        CompletableFuture<JsonObject> fut = new CompletableFuture<>();
        pending.put(cid, fut);

        ScheduledFuture<?> timeoutTask;
        try {
            timeoutTask = rpcTimeouts.schedule(() -> {
                if (pending.remove(cid, fut)) {
                    fut.completeExceptionally(new TimeoutException(
                            "RPC " + action + " timed out after " + resolvedTimeout.toMillis() + " ms"));
                }
            }, resolvedTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (RuntimeException error) {
            pending.remove(cid, fut);
            fut.completeExceptionally(error);
            return fut;
        }

        fut.whenComplete((ignored, error) -> {
            pending.remove(cid, fut);
            timeoutTask.cancel(false);
        });

        if (!send(env)) {
            if (pending.remove(cid, fut)) {
                fut.completeExceptionally(new IllegalStateException("Failed to send message " + action));
            }
        }

        return fut;
    }

    public <Req, Res> CompletableFuture<Res> call(String action,
                                                  Req req,
                                                  Class<Req> reqType,
                                                  Class<Res> resType) {

        return call(action, req, reqType, resType, defaultRpcTimeout);
    }

    public <Req, Res> CompletableFuture<Res> call(String action,
                                                  Req req,
                                                  Class<Req> reqType,
                                                  Class<Res> resType,
                                                  Duration timeout) {

        return callTyped(null, action, req, reqType, resType, false, timeout);
    }

    @SuppressWarnings("unchecked")
    public <R> CompletableFuture<R> call(RpcMessage request) {
        return (CompletableFuture<R>) callRpcTo(null, request, defaultRpcTimeout);
    }

    @SuppressWarnings("unchecked")
    public <R> CompletableFuture<R> call(RpcMessage request, Duration timeout) {
        return (CompletableFuture<R>) callRpcTo(null, request, timeout);
    }

    @SuppressWarnings("unchecked")
    public <R> CompletableFuture<R> callTo(String targetInstanceId, RpcMessage request) {
        return (CompletableFuture<R>) callRpcTo(targetInstanceId, request, defaultRpcTimeout);
    }

    @SuppressWarnings("unchecked")
    public <R> CompletableFuture<R> callTo(String targetInstanceId, RpcMessage request, Duration timeout) {
        return (CompletableFuture<R>) callRpcTo(targetInstanceId, request, timeout);
    }

    @Override
    public <Req, Res> CompletableFuture<Res> call(Req request,
                                                  Class<Res> responseType) {

        Class<?> reqType = request.getClass();
        BusAction action = requiredAction(reqType);
        return callTyped(null, action.value(), request, reqType, responseType,
                runOnMainThread(reqType), defaultRpcTimeout);
    }

    @Override
    public <Req, Res> CompletableFuture<Res> call(Req request, Class<Res> responseType, Duration timeout) {
        Class<?> reqType = request.getClass();
        BusAction action = requiredAction(reqType);
        return callTyped(null, action.value(), request, reqType, responseType, runOnMainThread(reqType), timeout);
    }

    @Override
    public <Req, Res> CompletableFuture<Res> callTo(String targetInstanceId,
                                                    Req request,
                                                    Class<Res> responseType) {

        Class<?> reqType = request.getClass();
        BusAction action = requiredAction(reqType);
        return callTyped(targetInstanceId, action.value(), request, reqType, responseType,
                runOnMainThread(reqType), defaultRpcTimeout);
    }

    @Override
    public <Req, Res> CompletableFuture<Res> callTo(String targetInstanceId, Req request,
                                                    Class<Res> responseType, Duration timeout) {
        Class<?> reqType = request.getClass();
        BusAction action = requiredAction(reqType);
        return callTyped(targetInstanceId, action.value(), request, reqType, responseType,
                runOnMainThread(reqType), timeout);
    }

    private void publishEvent(String targetInstanceId, BusMessage event) {
        if (!isRunning()) {
            throw new IllegalStateException("Message bus is not started");
        }

        Class<?> eventType = event.getClass();

        Envelope env = new Envelope();
        env.kind = "evt";
        env.action = actionName(eventType);
        env.mid = UUID.randomUUID().toString();
        env.target = targetInstanceId;
        env.payload = adapter(eventType).serialize(event);

        send(env);
    }

    private <R> CompletableFuture<R> callRpcTo(String targetInstanceId, RpcMessage request, Duration timeout) {
        Class<?> requestType = request.getClass();
        BusAction action = requiredAction(requestType);

        if (action.response() == Void.class)
            throw new IllegalArgumentException("This @BusAction is an event, not a RPC");

        @SuppressWarnings("unchecked")
        Class<R> responseType = (Class<R>) action.response();

        return callTyped(targetInstanceId, action.value(), request, requestType, responseType,
                runOnMainThread(requestType), timeout);
    }

    private <Res> CompletableFuture<Res> callTyped(String targetInstanceId,
                                                   String action,
                                                   Object request,
                                                   Class<?> requestType,
                                                   Class<Res> responseType,
                                                   boolean completeOnMainThread,
                                                   Duration timeout) {

        JsonObject payload = adapter(requestType).serialize(request);
        CompletableFuture<JsonObject> raw = targetInstanceId == null
                ? callRaw(action, payload, timeout)
                : callRawTo(targetInstanceId, action, payload, timeout);

        return deserializeResponse(raw, responseType, adapter(responseType), completeOnMainThread);
    }

    private <Res> CompletableFuture<Res> deserializeResponse(CompletableFuture<JsonObject> raw,
                                                             Class<Res> responseType,
                                                             BusTypeAdapter<Object> responseAdapter,
                                                             boolean completeOnMainThread) {

        CompletableFuture<Res> out = new CompletableFuture<>();

        raw.whenComplete((json, ex) -> {
            Runnable run = () -> {
                if (ex != null) {
                    out.completeExceptionally(ex);
                    return;
                }

                try {
                    out.complete(responseType.cast(responseAdapter.deserialize(json)));
                } catch (Exception deserializeError) {
                    out.completeExceptionally(deserializeError);
                }
            };

            if (completeOnMainThread) {
                try {
                    mainThreadExecutor.accept(run);
                } catch (Throwable executorError) {
                    out.completeExceptionally(executorError);
                }
            } else run.run();
        });

        out.whenComplete((ignored, error) -> {
            if (out.isCancelled()) raw.cancel(false);
        });

        return out;
    }

    @Override
    public void register(Class<?> clazz) {
        BusAction a = requiredAction(clazz);

        selfReceive.put(a.value(), a.receiveOwnMessages());

        if (RpcMessage.class.isAssignableFrom(clazz) && a.response() != Void.class) {

            rpcHandlers.put(a.value(), new RpcEntry(adapter(clazz)));

        } else if (BusMessage.class.isAssignableFrom(clazz)) {
            @SuppressWarnings("unchecked")
            Class<? extends BusMessage> eventClass = (Class<? extends BusMessage>) clazz;
            registerEvent(eventClass, BusMessage::execute);

        } else {
            throw new IllegalArgumentException("Class must implement BusMessage or RpcMessage");
        }
    }

    @Override
    public <T extends BusMessage> void register(Class<T> clazz, BusHandler<T> handler) {
        if (handler == null)
            throw new IllegalArgumentException("Handler cannot be null");

        BusAction a = requiredAction(clazz);

        if (RpcMessage.class.isAssignableFrom(clazz))
            throw new IllegalArgumentException("Use register(Class<?>) for RPC messages");

        selfReceive.put(a.value(), a.receiveOwnMessages());
        registerEvent(clazz, handler);
    }

    private <T extends BusMessage> void registerEvent(Class<T> clazz, BusHandler<T> handler) {
        BusAction a = requiredAction(clazz);

        @SuppressWarnings("unchecked")
        BusTypeAdapter<T> ad =
                (BusTypeAdapter<T>) BusAdapterRegistry.ensureAdapter((Class) clazz);

        eventHandlers.put(a.value(), new EventEntry<>(ad, handler));
    }

    private void onIncoming(JsonObject wire) {
        if (!isRunning()) return;
        maybeGcSeen();

        Envelope env = fromWire(wire);
        if (env == null || env.kind == null || env.action == null) return;

        boolean isSelf =
                env.sender != null && env.sender.equals(backend.getInstanceId());

        if (env.target != null && !env.target.equals(backend.getInstanceId())) return;

        if ("req".equals(env.kind) || "evt".equals(env.kind)) {
            boolean allowSelf = selfReceive.getOrDefault(env.action, false);
            if (isSelf && !allowSelf) return;
        }

        if ("evt".equals(env.kind)) {
            if (env.mid != null && isSeen("E:" + env.mid)) return;
        } else if ("res".equals(env.kind) && env.cid != null) {
            if (isSeen("C:res:" + env.cid)) return;
        }

        switch (env.kind) {
            case "res" -> handleResponse(env);
            case "req" -> handleRequest(env);
            case "evt" -> handleEvent(env);
        }
    }

    private void handleResponse(Envelope env) {
        if (env.cid == null) return;

        CompletableFuture<JsonObject> fut = pending.remove(env.cid);
        if (fut == null) return;

        if (env.error != null)
            fut.completeExceptionally(new RuntimeException(env.error));
        else
            fut.complete(env.payload != null ? env.payload : new JsonObject());
    }

    private void handleEvent(Envelope env) {
        EventEntry<?> e = eventHandlers.get(env.action);
        if (e == null) return;

        @SuppressWarnings("unchecked")
        EventEntry<BusMessage> ee = (EventEntry<BusMessage>) e;

        try {
            BusMessage msg =
                    ee.adapter.deserialize(env.payload != null ? env.payload : new JsonObject());

            Runnable run = () -> {
                try {
                    ee.handler.handle(msg);
                } catch (Exception ex) {
                    logger.warn("[MessageBus] Failed to handle event {}: {}", env.action, message(ex));
                }
            };

            if (runOnMainThread(msg.getClass())) mainThreadExecutor.accept(run);
            else run.run();
        } catch (Exception ex) {
            logger.warn("[MessageBus] Failed to deserialize event {}: {}", env.action, message(ex));
        }
    }

    private void handleRequest(Envelope env) {
        RpcEntry re = rpcHandlers.get(env.action);
        if (re == null || env.cid == null) return;

        RequestExecution execution = new RequestExecution();
        if (env.sender != null && !env.sender.isBlank()) {
            RequestKey key = new RequestKey(env.sender, env.cid);
            RequestExecution existing = requestExecutions.putIfAbsent(key, execution);
            if (existing != null) {
                existing.response.thenAccept(response -> {
                    if (response != null) send(response);
                });
                return;
            }
        }

        Object req;
        try {
            req = re.reqAdapter.deserialize(env.payload != null ? env.payload : new JsonObject());
        } catch (Throwable error) {
            completeRequest(execution, errorResponse(env, error));
            return;
        }

        boolean onMain = runOnMainThread(req.getClass());

        Runnable run = () -> {
            if (!isRunning()) {
                execution.complete(null);
                return;
            }

            try {
                if (!(req instanceof RpcMessage ra))
                    throw new IllegalStateException("Endpoint must implement RpcMessage");

                Object result = ra.handle();
                if (result == null) {
                    execution.complete(null);
                    return;
                }

                Envelope out = new Envelope();
                out.kind = "res";
                out.action = env.action;
                out.cid = env.cid;
                out.target = env.sender;
                out.payload = adapter(result.getClass()).serialize(result);

                completeRequest(execution, out);
            } catch (Throwable error) {
                completeRequest(execution, errorResponse(env, error));
            }
        };

        try {
            if (onMain) mainThreadExecutor.accept(run);
            else run.run();
        } catch (Throwable error) {
            completeRequest(execution, errorResponse(env, error));
        }
    }

    private void completeRequest(RequestExecution execution, Envelope response) {
        if (response != null) send(response);
        execution.complete(response);
    }

    private Envelope errorResponse(Envelope request, Throwable error) {
        Envelope out = new Envelope();
        out.kind = "res";
        out.action = request.action;
        out.cid = request.cid;
        out.target = request.sender;
        out.error = message(error);
        out.payload = new JsonObject();
        return out;
    }

    private boolean send(Envelope env) {
        if (!isRunning()) return false;

        JsonObject o = new JsonObject();

        o.addProperty("action", env.action);
        o.addProperty("kind", env.kind);

        if (env.cid != null) o.addProperty("cid", env.cid);
        if (env.mid != null) o.addProperty("mid", env.mid);
        o.addProperty("__sender", backend.getInstanceId());
        if (env.target != null && !env.target.isEmpty()) {
            o.addProperty("__target", env.target);
        }
        o.addProperty("ts", System.currentTimeMillis());

        JsonObject content = new JsonObject();
        if (env.payload != null) content.add("payload", env.payload);
        if (env.error != null) content.addProperty("error", env.error);

        if (crypto.enabled()) {
            try {
                o.add("payload", crypto.encrypt(content, associatedData(o)));
            } catch (Exception ex) {
                logger.warn("[MessageBus] Failed to encrypt message {}: {}", env.action, message(ex));
                return false;
            }
        } else {
            if (env.payload != null) o.add("payload", env.payload);
            if (env.error != null) o.addProperty("error", env.error);
        }

        try {
            backend.publish(channel, o);
            return true;
        } catch (Exception ex) {
            logger.warn("[MessageBus] Failed to publish message {}: {}", env.action, message(ex));
            return false;
        }
    }

    private Envelope fromWire(JsonObject o) {
        try {
            Envelope e = new Envelope();

            e.kind = o.has("kind") ? o.get("kind").getAsString() : null;
            e.action = o.has("action") ? o.get("action").getAsString() : null;
            e.cid = o.has("cid") ? o.get("cid").getAsString() : null;
            e.mid = o.has("mid") ? o.get("mid").getAsString() : null;

            e.sender =
                    o.has("__sender")
                            ? o.get("__sender").getAsString()
                            : null;

            e.target =
                    o.has("__target")
                            ? o.get("__target").getAsString()
                            : null;

            e.ts =
                    o.has("ts")
                            ? o.get("ts").getAsLong()
                            : 0L;

            JsonObject wirePayload =
                    o.has("payload") && o.get("payload").isJsonObject()
                            ? o.getAsJsonObject("payload")
                            : new JsonObject();

            if (AesGcmMessageCrypto.isEncrypted(wirePayload)) {
                if (!crypto.enabled()) {
                    logger.warn("[MessageBus] Received encrypted message for action {} but encryption is disabled", e.action);
                    return null;
                }

                JsonObject content = crypto.decrypt(wirePayload, associatedData(o));
                e.payload =
                        content.has("payload") && content.get("payload").isJsonObject()
                                ? content.getAsJsonObject("payload")
                                : new JsonObject();
                e.error =
                        content.has("error") && !content.get("error").isJsonNull()
                                ? content.get("error").getAsString()
                                : null;
            } else {
                if (crypto.enabled()) {
                    logger.warn("[MessageBus] Received plaintext message for action {} while encryption is enabled", e.action);
                    return null;
                }

                e.payload = wirePayload;
                e.error =
                        o.has("error") && !o.get("error").isJsonNull()
                                ? o.get("error").getAsString()
                                : null;
            }

            return e;

        } catch (Exception ex) {
            logger.warn("[MessageBus] Ignored invalid message envelope: {}", message(ex));
            return null;
        }
    }

    private static byte[] associatedData(JsonObject o) {
        String data = "v1"
                + "\nkind=" + stringProp(o, "kind")
                + "\naction=" + stringProp(o, "action")
                + "\ncid=" + stringProp(o, "cid")
                + "\nmid=" + stringProp(o, "mid")
                + "\nsender=" + stringProp(o, "__sender")
                + "\ntarget=" + stringProp(o, "__target")
                + "\nts=" + stringProp(o, "ts");
        return data.getBytes(StandardCharsets.UTF_8);
    }

    private static String stringProp(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull()
                ? o.get(key).getAsString()
                : "";
    }

    private static String actionName(Class<?> type) {
        return requiredAction(type).value();
    }

    private static boolean runOnMainThread(Class<?> type) {
        BusAction action = type == null ? null : type.getAnnotation(BusAction.class);
        return action != null && action.runOnMainThread();
    }

    private static BusAction requiredAction(Class<?> type) {
        BusAction action = type == null ? null : type.getAnnotation(BusAction.class);
        if (action == null || action.value().isEmpty()) {
            throw new IllegalArgumentException("Missing @BusAction on " + (type == null ? "null" : type.getName()));
        }
        return action;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BusTypeAdapter<Object> adapter(Class<?> type) {
        return (BusTypeAdapter<Object>) BusAdapterRegistry.ensureAdapter((Class) type);
    }

    private static String message(Throwable throwable) {
        return throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
    }

    private static Duration requireTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("RPC timeout must be positive");
        }
        return timeout;
    }

    private static ScheduledThreadPoolExecutor createTimeoutExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "UtilsZ-MessageBus-RpcTimeout");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        return CompletableFuture.failedFuture(error);
    }

    private boolean isRunning() {
        return started.get() && !closed.get();
    }

    private boolean isSeen(String id) {
        long now = System.currentTimeMillis();
        Long prev = seen.putIfAbsent(id, now);
        return prev != null;
    }

    private void maybeGcSeen() {
        long now = System.currentTimeMillis();

        if (lastGcAt == 0L) {
            lastGcAt = now;
            return;
        }

        long everyMs = 30_000L;
        if ((now - lastGcAt) < everyMs) return;

        lastGcAt = now;

        long cutoff = now - 60_000L;

        for (Map.Entry<String, Long> e : seen.entrySet()) {
            if (e.getValue() < cutoff) {
                seen.remove(e.getKey(), e.getValue());
            }
        }

        for (Map.Entry<RequestKey, RequestExecution> e : requestExecutions.entrySet()) {
            long completedAt = e.getValue().completedAt;
            if (completedAt > 0L && completedAt < cutoff) {
                requestExecutions.remove(e.getKey(), e.getValue());
            }
        }

        if (seen.size() > 50_000) {
            seen.clear();
        }
    }
}
