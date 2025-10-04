package fr.nivcoo.utilsz.redis.bus;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.redis.*;
import fr.nivcoo.utilsz.redis.rpc.RpcAnnotated;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RedisBus {

    private static final class Envelope {
        String kind;
        String action;
        String cid;
        String mid;
        JsonObject payload;
        String error;
        String sender;
        long ts;
    }

    private static final class EventEntry<T extends RedisSerializable> {
        final RedisTypeAdapter<T> adapter;
        final RedisHandler<T> handler;
        EventEntry(RedisTypeAdapter<T> a, RedisHandler<T> h) { adapter = a; handler = h; }
    }

    private static final class RpcEntry {
        final RedisTypeAdapter<Object> reqAdapter;
        RpcEntry(RedisTypeAdapter<Object> ra) { this.reqAdapter = ra; }
    }

    private final JavaPlugin plugin;
    private final RedisManager redis;
    private final String channel;

    private final ConcurrentMap<String, EventEntry<?>> eventHandlers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RpcEntry> rpcHandlers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> seen = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> selfReceive = new ConcurrentHashMap<>();

    private final ScheduledExecutorService gc = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "RedisBus-GC");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean started = new AtomicBoolean(false);

    public RedisBus(JavaPlugin plugin, RedisManager redis, String channel) {
        this.plugin = plugin;
        this.redis = redis;
        this.channel = channel;

        redis.subscribeRaw(channel, this::onIncoming);

        gc.scheduleAtFixedRate(this::gcSeen, 30, 30, TimeUnit.SECONDS);
    }

    public void start() {
        if (started.compareAndSet(false, true)) redis.start();
    }

    public void close() {
        pending.forEach((cid, fut) -> fut.completeExceptionally(new CancellationException("Bus closed")));
        pending.clear();
        started.set(false);
        gc.shutdownNow();
    }

    public void publish(RedisSerializable evt) {
        @SuppressWarnings({"rawtypes","unchecked"})
        RedisTypeAdapter<Object> ad = (RedisTypeAdapter) RedisAdapterRegistry.ensureAdapter((Class) evt.getClass());
        Envelope env = new Envelope();
        env.kind = "evt";
        env.action = evt.getAction();
        env.mid = UUID.randomUUID().toString();
        env.payload = ad.serialize(evt);
        send(env);
    }

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

    public <Req, Res> CompletableFuture<Res> call(String action, Req req, Class<Req> reqType, Class<Res> resType) {
        @SuppressWarnings({"rawtypes","unchecked"})
        RedisTypeAdapter<Object> reqA = (RedisTypeAdapter) RedisAdapterRegistry.ensureAdapter((Class) reqType);
        @SuppressWarnings({"rawtypes","unchecked"})
        RedisTypeAdapter<Object> resA = (RedisTypeAdapter) RedisAdapterRegistry.ensureAdapter((Class) resType);
        JsonObject payload = reqA.serialize(req);
        return callRaw(action, payload).thenApply(json -> resType.cast(resA.deserialize(json)));
    }

    public <R> CompletableFuture<R> call(RpcAnnotated request) {
        Class<?> c = request.getClass();
        RedisAction a = c.getAnnotation(RedisAction.class);
        if (a == null || a.value().isEmpty())
            throw new IllegalArgumentException("Missing @RedisAction on " + c.getName());
        if (a.response() == Void.class)
            throw new IllegalArgumentException("This @RedisAction is an event, not a RPC.");

        @SuppressWarnings({"rawtypes","unchecked"})
        RedisTypeAdapter<Object> reqA = (RedisTypeAdapter) RedisAdapterRegistry.ensureAdapter((Class) c);
        @SuppressWarnings({"rawtypes","unchecked"})
        RedisTypeAdapter<Object> resA = (RedisTypeAdapter) RedisAdapterRegistry.ensureAdapter((Class) a.response());

        JsonObject payload = reqA.serialize(request);
        CompletableFuture<JsonObject> raw = callRaw(a.value(), payload);

        boolean onMain = request.runOnMainThread();
        CompletableFuture<R> out = new CompletableFuture<>();
        raw.whenComplete((json, ex) -> {
            Runnable r = () -> {
                if (ex != null) out.completeExceptionally(ex);
                else out.complete((R) a.response().cast(resA.deserialize(json)));
            };
            if (onMain) Bukkit.getScheduler().runTask(plugin, r); else r.run();
        });
        return out;
    }

    public <Req, Res> CompletableFuture<Res> call(Req request, Class<Res> responseType) {
        Class<?> reqType = request.getClass();
        RedisAction action = reqType.getAnnotation(RedisAction.class);
        if (action == null || action.value().isEmpty())
            throw new IllegalArgumentException("Missing @RedisAction on " + reqType.getName());

        @SuppressWarnings({"rawtypes","unchecked"})
        RedisTypeAdapter<Object> reqA = (RedisTypeAdapter) RedisAdapterRegistry.ensureAdapter((Class) reqType);
        @SuppressWarnings({"rawtypes","unchecked"})
        RedisTypeAdapter<Object> resA = (RedisTypeAdapter) RedisAdapterRegistry.ensureAdapter((Class) responseType);

        JsonObject payload = reqA.serialize(request);
        CompletableFuture<JsonObject> raw = callRaw(action.value(), payload);

        boolean onMain = (request instanceof RpcAnnotated ra) && ra.runOnMainThread();
        CompletableFuture<Res> out = new CompletableFuture<>();
        raw.whenComplete((json, ex) -> {
            Runnable r = () -> {
                if (ex != null) out.completeExceptionally(ex);
                else out.complete(responseType.cast(resA.deserialize(json)));
            };
            if (onMain) Bukkit.getScheduler().runTask(plugin, r); else r.run();
        });
        return out;
    }

    public void register(Class<?> clazz) {
        RedisAction a = clazz.getAnnotation(RedisAction.class);
        if (a == null || a.value().isEmpty())
            throw new IllegalArgumentException("Missing @RedisAction on " + clazz.getName());

        selfReceive.put(a.value(), a.receiveOwnMessages());

        if (RpcAnnotated.class.isAssignableFrom(clazz) && a.response() != Void.class) {
            @SuppressWarnings({"rawtypes","unchecked"})
            RedisTypeAdapter<Object> reqA = (RedisTypeAdapter) RedisAdapterRegistry.ensureAdapter((Class) clazz);
            rpcHandlers.put(a.value(), new RpcEntry(reqA));
        } else if (RedisSerializable.class.isAssignableFrom(clazz)) {
            @SuppressWarnings({"rawtypes","unchecked"})
            RedisTypeAdapter<RedisSerializable> ad = (RedisTypeAdapter) RedisAdapterRegistry.ensureAdapter((Class) clazz);
            eventHandlers.put(a.value(), new EventEntry<>(ad, RedisSerializable::execute));
        } else {
            throw new IllegalArgumentException("Class must implement RedisSerializable or RpcAnnotated");
        }
    }

    private void onIncoming(JsonObject wire) {
        Envelope env = fromWire(wire);
        if (env == null || env.kind == null || env.action == null) return;

        boolean isSelf = env.sender != null && env.sender.equals(redis.getInstanceId());

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
        if (env.error != null) fut.completeExceptionally(new RuntimeException(env.error));
        else fut.complete(env.payload != null ? env.payload : new JsonObject());
    }

    private void handleEvent(Envelope env) {
        EventEntry<?> e = eventHandlers.get(env.action);
        if (e == null) return;
        @SuppressWarnings("unchecked")
        EventEntry<RedisSerializable> ee = (EventEntry<RedisSerializable>) e;
        RedisSerializable msg = ee.adapter.deserialize(env.payload != null ? env.payload : new JsonObject());
        ee.handler.handle(msg);
    }

    private void handleRequest(Envelope env) {
        RpcEntry re = rpcHandlers.get(env.action);
        if (re == null || env.cid == null) return;

        Object req = re.reqAdapter.deserialize(env.payload != null ? env.payload : new JsonObject());
        boolean onMain = (req instanceof RpcAnnotated ra) && ra.runOnMainThread();

        Runnable run = () -> {
            try {
                if (!(req instanceof RpcAnnotated ra))
                    throw new IllegalStateException("Endpoint must implement RpcAnnotated");

                Object result = ra.handle();

                if (result == null) return;

                @SuppressWarnings({"rawtypes","unchecked"})
                RedisTypeAdapter<Object> resA = (RedisTypeAdapter) RedisAdapterRegistry.ensureAdapter((Class) result.getClass());

                Envelope out = new Envelope();
                out.kind = "res";
                out.action = env.action;
                out.cid = env.cid;
                out.payload = resA.serialize(result);
                send(out);

            } catch (Exception ex) {
                Envelope out = new Envelope();
                out.kind = "res";
                out.action = env.action;
                out.cid = env.cid;
                out.error = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                out.payload = new JsonObject();
                send(out);
            }
        };

        if (onMain) Bukkit.getScheduler().runTask(plugin, run); else run.run();
    }

    private void send(Envelope env) {
        JsonObject o = new JsonObject();
        o.addProperty("action", env.action);
        o.addProperty("kind", env.kind);
        if (env.cid != null) o.addProperty("cid", env.cid);
        if (env.mid != null) o.addProperty("mid", env.mid);
        if (env.error != null) o.addProperty("error", env.error);
        if (env.payload != null) o.add("payload", env.payload);
        o.addProperty("__sender", redis.getInstanceId());
        o.addProperty("ts", System.currentTimeMillis());
        redis.publish(channel, o);
    }

    private static Envelope fromWire(JsonObject o) {
        try {
            Envelope e = new Envelope();
            e.kind = o.has("kind") ? o.get("kind").getAsString() : null;
            e.action = o.has("action") ? o.get("action").getAsString() : null;
            e.cid = o.has("cid") ? o.get("cid").getAsString() : null;
            e.mid = o.has("mid") ? o.get("mid").getAsString() : null;
            e.payload = o.has("payload") && o.get("payload").isJsonObject() ? o.getAsJsonObject("payload") : new JsonObject();
            e.error = o.has("error") && !o.get("error").isJsonNull() ? o.get("error").getAsString() : null;
            e.sender = o.has("__sender") ? o.get("__sender").getAsString() : null;
            e.ts = o.has("ts") ? o.get("ts").getAsLong() : 0L;
            return e;
        } catch (Exception ignore) { return null; }
    }

    private boolean isSeen(String id) {
        long now = System.currentTimeMillis();
        Long prev = seen.putIfAbsent(id, now);
        return prev != null;
    }

    private void gcSeen() {
        long cutoff = System.currentTimeMillis() - 60_000L;
        for (Map.Entry<String, Long> e : seen.entrySet()) {
            if (e.getValue() < cutoff) seen.remove(e.getKey(), e.getValue());
        }
        if (seen.size() > 50_000) seen.clear();
    }
}
