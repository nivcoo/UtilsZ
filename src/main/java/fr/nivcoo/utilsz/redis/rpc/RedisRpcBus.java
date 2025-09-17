// fr/nivcoo/utilsz/redis/rpc/RedisRpcBus.java
package fr.nivcoo.utilsz.redis.rpc;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.redis.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class RedisRpcBus {

    private static final class RpcEnvelope {
        String action, kind, cid;
        JsonObject payload;
        String error;
    }

    private static final class RpcEntry {
        final String action;
        final RedisTypeAdapter<Object> reqAdapter;

        RpcEntry(String action, RedisTypeAdapter<Object> ra) {
            this.action = action;
            this.reqAdapter = ra;
        }
    }

    private final JavaPlugin plugin;
    private final RedisManager redis;
    private final String channel;

    private final Map<String, RpcEntry> handlers = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final Supplier<String> cidSupplier = () -> UUID.randomUUID().toString();

    public RedisRpcBus(JavaPlugin plugin, RedisManager redis, String channel) {
        this.plugin = plugin;
        this.redis = redis;
        this.channel = channel;
        redis.subscribe(channel, (ch, msg) -> onIncoming(msg));
        redis.start();
    }

    public CompletableFuture<JsonObject> callRaw(String action, JsonObject jsonReq) {
        String cid = cidSupplier.get();
        RpcEnvelope env = new RpcEnvelope();
        env.action = action;
        env.kind = "req";
        env.cid = cid;
        env.payload = jsonReq;
        CompletableFuture<JsonObject> fut = new CompletableFuture<>();
        pending.put(cid, fut);
        redis.publish(channel, toWire(env));
        return fut;
    }

    public <Req, Res> CompletableFuture<Res> call(String action, Req request, Class<Req> reqType, Class<Res> resType) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        RedisTypeAdapter<Object> reqA = (RedisTypeAdapter) RedisAdapterRegistry.ensureAdapter((Class) reqType);
        @SuppressWarnings({"rawtypes", "unchecked"})
        RedisTypeAdapter<Object> resA = (RedisTypeAdapter) RedisAdapterRegistry.ensureAdapter((Class) resType);
        JsonObject payload = reqA.serialize(request);
        return callRaw(action, payload).thenApply(json -> resType.cast(resA.deserialize(json)));
    }

    public <R> CompletableFuture<R> call(RpcAnnotated request) {
        Class<?> endpointClass = request.getClass();
        RedisAction a = endpointClass.getAnnotation(RedisAction.class);
        RpcResponse r = endpointClass.getAnnotation(RpcResponse.class);
        if (a == null || a.value().isEmpty())
            throw new IllegalArgumentException("Missing @RedisAction on " + endpointClass.getName());
        if (r == null)
            throw new IllegalArgumentException("Missing @RpcResponse on " + endpointClass.getName());

        @SuppressWarnings("unchecked")
        Class<R> resType = (Class<R>) r.value();

        @SuppressWarnings({"rawtypes", "unchecked"})
        RedisTypeAdapter<Object> reqA = (RedisTypeAdapter) RedisAdapterRegistry.ensureAdapter((Class) endpointClass);
        @SuppressWarnings({"rawtypes", "unchecked"})
        RedisTypeAdapter<Object> resA = (RedisTypeAdapter) RedisAdapterRegistry.ensureAdapter((Class) resType);

        JsonObject payload = reqA.serialize(request);
        return callRaw(a.value(), payload).thenApply(json -> resType.cast(resA.deserialize(json)));
    }

    public <R> CompletableFuture<R> call(RpcAnnotated request, Class<R> resType) {
        Class<?> endpointClass = request.getClass();
        RedisAction a = endpointClass.getAnnotation(RedisAction.class);
        if (a == null || a.value().isEmpty())
            throw new IllegalArgumentException("Missing @RedisAction on " + endpointClass.getName());

        @SuppressWarnings({"rawtypes", "unchecked"})
        RedisTypeAdapter<Object> reqA = (RedisTypeAdapter) RedisAdapterRegistry.ensureAdapter((Class) endpointClass);
        @SuppressWarnings({"rawtypes", "unchecked"})
        RedisTypeAdapter<Object> resA = (RedisTypeAdapter) RedisAdapterRegistry.ensureAdapter((Class) resType);

        JsonObject payload = reqA.serialize(request);
        return callRaw(a.value(), payload).thenApply(json -> resType.cast(resA.deserialize(json)));
    }

    public void register(Class<? extends RpcAnnotated> endpointClass) {
        RedisAction a = endpointClass.getAnnotation(RedisAction.class);
        if (a == null || a.value().isEmpty())
            throw new IllegalArgumentException("Missing @RedisAction on " + endpointClass.getName());

        @SuppressWarnings({"rawtypes", "unchecked"})
        RedisTypeAdapter<Object> reqA = (RedisTypeAdapter) RedisAdapterRegistry.ensureAdapter((Class) endpointClass);
        handlers.put(a.value(), new RpcEntry(a.value(), reqA));
    }

    private void onIncoming(JsonObject wire) {
        RpcEnvelope env = fromWire(wire);
        if (env == null || env.action == null || env.kind == null || env.cid == null) return;

        if ("res".equals(env.kind)) {
            CompletableFuture<JsonObject> fut = pending.remove(env.cid);
            if (fut != null) {
                if (env.error != null) fut.completeExceptionally(new RuntimeException(env.error));
                else fut.complete(env.payload);
            }
            return;
        }

        RpcEntry entry = handlers.get(env.action);
        if (entry != null) handleRequest(entry, env);
    }

    private void handleRequest(RpcEntry entry, RpcEnvelope env) {
        Object req = entry.reqAdapter.deserialize(env.payload);
        boolean mainThread = (req instanceof RpcAnnotated ra) && ra.runOnMainThread();

        Runnable run = () -> {
            try {
                if (!(req instanceof RpcAnnotated ra))
                    throw new IllegalStateException("Endpoint must implement RpcAnnotated");

                Object result;
                try {
                    result = ra.handle();
                } catch (RpcIgnoreException ignore) {
                    return;
                }

                if (result == null) {
                    return;
                }

                @SuppressWarnings({"rawtypes", "unchecked"})
                RedisTypeAdapter<Object> resA =
                        (RedisTypeAdapter) RedisAdapterRegistry.ensureAdapter((Class) result.getClass());

                RpcEnvelope out = new RpcEnvelope();
                out.action = entry.action;
                out.kind = "res";
                out.cid = env.cid;
                out.payload = resA.serialize(result);
                redis.publish(channel, toWire(out));

            } catch (Exception ex) {
                RpcEnvelope out = new RpcEnvelope();
                out.action = entry.action;
                out.kind = "res";
                out.cid = env.cid;
                out.error = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                out.payload = new JsonObject();
                redis.publish(channel, toWire(out));
            }
        };

        if (mainThread) org.bukkit.Bukkit.getScheduler().runTask(plugin, run);
        else run.run();
    }


    private static JsonObject toWire(RpcEnvelope env) {
        JsonObject o = new JsonObject();
        o.addProperty("action", env.action);
        o.addProperty("kind", env.kind);
        o.addProperty("cid", env.cid);
        if (env.payload != null) o.add("payload", env.payload);
        if (env.error != null) o.addProperty("error", env.error);
        return o;
    }

    private static RpcEnvelope fromWire(JsonObject o) {
        RpcEnvelope e = new RpcEnvelope();
        try {
            e.action = o.get("action").getAsString();
            e.kind = o.get("kind").getAsString();
            e.cid = o.get("cid").getAsString();
            e.payload = o.has("payload") ? o.getAsJsonObject("payload") : new JsonObject();
            e.error = o.has("error") && !o.get("error").isJsonNull() ? o.get("error").getAsString() : null;
            return e;
        } catch (Exception ignore) {
            return null;
        }
    }
}
