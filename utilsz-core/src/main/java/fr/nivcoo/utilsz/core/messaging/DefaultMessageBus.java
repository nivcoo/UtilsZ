package fr.nivcoo.utilsz.core.messaging;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.core.messaging.crypto.AesGcmMessageCrypto;
import fr.nivcoo.utilsz.core.messaging.crypto.MessageCrypto;
import fr.nivcoo.utilsz.core.messaging.crypto.NoopMessageCrypto;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

    private final MessageBackend backend;
    private final String channel;
    private final Consumer<Runnable> mainThreadExecutor;
    private final Logger logger;
    private final MessageCrypto crypto;

    private final ConcurrentMap<String, EventEntry<?>> eventHandlers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RpcEntry> rpcHandlers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> seen = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> selfReceive = new ConcurrentHashMap<>();

    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile long lastGcAt = 0L;

    public DefaultMessageBus(MessageBackend backend,
                             String channel,
                             Consumer<Runnable> mainThreadExecutor,
                             Logger logger) {

        this(backend, channel, mainThreadExecutor, logger, NoopMessageCrypto.INSTANCE);
    }

    public DefaultMessageBus(MessageBackend backend,
                             String channel,
                             Consumer<Runnable> mainThreadExecutor,
                             Logger logger,
                             MessageCrypto crypto) {

        this.backend = backend;
        this.channel = channel;
        this.mainThreadExecutor = mainThreadExecutor;
        this.logger = logger;
        this.crypto = crypto == null ? NoopMessageCrypto.INSTANCE : crypto;

        BusAdapterRegistry.registerBuiltins();

        backend.onError(t ->
                logger.warn("[MessageBus] Backend error: {}", t.getMessage())
        );

        backend.subscribeRaw(channel, this::onIncoming);
    }

    @Override
    public void start() {
        if (started.compareAndSet(false, true)) {
            backend.start();
        }
    }

    @Override
    public void close() {
        pending.forEach((cid, fut) ->
                fut.completeExceptionally(new CancellationException("Bus closed"))
        );
        pending.clear();
        started.set(false);
        backend.close();
    }

    @Override
    public void publish(BusMessage evt) {
        @SuppressWarnings("unchecked")
        BusTypeAdapter<Object> ad =
                (BusTypeAdapter<Object>) BusAdapterRegistry.ensureAdapter((Class) evt.getClass());

        Envelope env = new Envelope();
        env.kind = "evt";
        env.action = evt.getAction();
        env.mid = UUID.randomUUID().toString();
        env.payload = ad.serialize(evt);

        send(env);
    }

    @Override
    public CompletableFuture<JsonObject> callRaw(String action, JsonObject payload) {
        String cid = UUID.randomUUID().toString();

        Envelope env = new Envelope();
        env.kind = "req";
        env.action = action;
        env.cid = cid;
        env.payload = payload;

        CompletableFuture<JsonObject> fut = new CompletableFuture<>();
        pending.put(cid, fut);

        send(env);
        return fut;
    }

    public <Req, Res> CompletableFuture<Res> call(String action,
                                                  Req req,
                                                  Class<Req> reqType,
                                                  Class<Res> resType) {

        @SuppressWarnings("unchecked")
        BusTypeAdapter<Object> reqA =
                (BusTypeAdapter<Object>) BusAdapterRegistry.ensureAdapter((Class) reqType);

        @SuppressWarnings("unchecked")
        BusTypeAdapter<Object> resA =
                (BusTypeAdapter<Object>) BusAdapterRegistry.ensureAdapter((Class) resType);

        JsonObject payload = reqA.serialize(req);

        return callRaw(action, payload)
                .thenApply(json -> resType.cast(resA.deserialize(json)));
    }

    @SuppressWarnings("unchecked")
    public <R> CompletableFuture<R> call(RpcMessage request) {
        Class<?> c = request.getClass();
        BusAction a = c.getAnnotation(BusAction.class);

        if (a == null || a.value().isEmpty())
            throw new IllegalArgumentException("Missing @BusAction on " + c.getName());

        if (a.response() == Void.class)
            throw new IllegalArgumentException("This @BusAction is an event, not a RPC");

        BusTypeAdapter<Object> reqA =
                (BusTypeAdapter<Object>) BusAdapterRegistry.ensureAdapter((Class) c);

        BusTypeAdapter<Object> resA =
                (BusTypeAdapter<Object>) BusAdapterRegistry.ensureAdapter((Class) a.response());

        JsonObject payload = reqA.serialize(request);
        CompletableFuture<JsonObject> raw = callRaw(a.value(), payload);

        boolean onMain = request.runOnMainThread();
        CompletableFuture<R> out = new CompletableFuture<>();

        raw.whenComplete((json, ex) -> {
            Runnable r = () -> {
                if (ex != null) {
                    out.completeExceptionally(ex);
                } else {
                    out.complete((R) a.response().cast(resA.deserialize(json)));
                }
            };

            if (onMain) mainThreadExecutor.accept(r);
            else r.run();
        });

        return out;
    }

    public <Req, Res> CompletableFuture<Res> call(Req request,
                                                  Class<Res> responseType) {

        Class<?> reqType = request.getClass();
        BusAction action = reqType.getAnnotation(BusAction.class);

        if (action == null || action.value().isEmpty())
            throw new IllegalArgumentException("Missing @BusAction on " + reqType.getName());

        @SuppressWarnings("unchecked")
        BusTypeAdapter<Object> reqA =
                (BusTypeAdapter<Object>) BusAdapterRegistry.ensureAdapter((Class) reqType);

        @SuppressWarnings("unchecked")
        BusTypeAdapter<Object> resA =
                (BusTypeAdapter<Object>) BusAdapterRegistry.ensureAdapter((Class) responseType);

        JsonObject payload = reqA.serialize(request);
        CompletableFuture<JsonObject> raw = callRaw(action.value(), payload);

        boolean onMain = request instanceof RpcMessage ra && ra.runOnMainThread();
        CompletableFuture<Res> out = new CompletableFuture<>();

        raw.whenComplete((json, ex) -> {
            Runnable r = () -> {
                if (ex != null) out.completeExceptionally(ex);
                else out.complete(responseType.cast(resA.deserialize(json)));
            };

            if (onMain) mainThreadExecutor.accept(r);
            else r.run();
        });

        return out;
    }

    @Override
    public void register(Class<?> clazz) {
        BusAction a = clazz.getAnnotation(BusAction.class);

        if (a == null || a.value().isEmpty())
            throw new IllegalArgumentException("Missing @BusAction on " + clazz.getName());

        selfReceive.put(a.value(), a.receiveOwnMessages());

        if (RpcMessage.class.isAssignableFrom(clazz) && a.response() != Void.class) {

            @SuppressWarnings("unchecked")
            BusTypeAdapter<Object> reqA =
                    (BusTypeAdapter<Object>) BusAdapterRegistry.ensureAdapter((Class) clazz);

            rpcHandlers.put(a.value(), new RpcEntry(reqA));

        } else if (BusMessage.class.isAssignableFrom(clazz)) {

            @SuppressWarnings("unchecked")
            BusTypeAdapter<BusMessage> ad =
                    (BusTypeAdapter<BusMessage>) BusAdapterRegistry.ensureAdapter((Class) clazz);

            eventHandlers.put(a.value(), new EventEntry<>(ad, BusMessage::execute));

        } else {
            throw new IllegalArgumentException("Class must implement BusMessage or RpcMessage");
        }
    }

    private void onIncoming(JsonObject wire) {
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

        BusMessage msg =
                ee.adapter.deserialize(env.payload != null ? env.payload : new JsonObject());

        ee.handler.handle(msg);
    }

    private void handleRequest(Envelope env) {
        RpcEntry re = rpcHandlers.get(env.action);
        if (re == null || env.cid == null) return;

        Object req =
                re.reqAdapter.deserialize(env.payload != null ? env.payload : new JsonObject());

        boolean onMain = req instanceof RpcMessage ra && ra.runOnMainThread();

        Runnable run = () -> {
            try {
                if (!(req instanceof RpcMessage ra))
                    throw new IllegalStateException("Endpoint must implement RpcMessage");

                Object result = ra.handle();
                if (result == null) return;

                @SuppressWarnings("unchecked")
                BusTypeAdapter<Object> resA =
                        (BusTypeAdapter<Object>) BusAdapterRegistry.ensureAdapter(
                                (Class) result.getClass());

                Envelope out = new Envelope();
                out.kind = "res";
                out.action = env.action;
                out.cid = env.cid;
                out.target = env.sender;
                out.payload = resA.serialize(result);

                send(out);

            } catch (Exception ex) {
                Envelope out = new Envelope();
                out.kind = "res";
                out.action = env.action;
                out.cid = env.cid;
                out.target = env.sender;
                out.error = ex.getMessage() != null
                        ? ex.getMessage()
                        : ex.toString();
                out.payload = new JsonObject();

                send(out);
            }
        };

        if (onMain) mainThreadExecutor.accept(run);
        else run.run();
    }

    private void send(Envelope env) {
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
                logger.warn("[MessageBus] Failed to encrypt message {}: {}", env.action, ex.getMessage());
                return;
            }
        } else {
            if (env.payload != null) o.add("payload", env.payload);
            if (env.error != null) o.addProperty("error", env.error);
        }

        backend.publish(channel, o);
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
            logger.warn("[MessageBus] Ignored invalid message envelope: {}", ex.getMessage());
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

        if (seen.size() > 50_000) {
            seen.clear();
        }
    }
}
