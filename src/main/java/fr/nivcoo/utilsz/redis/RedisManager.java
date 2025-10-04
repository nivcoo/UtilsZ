package fr.nivcoo.utilsz.redis;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class RedisManager {

    private final JedisPool jedisPool;
    private final JavaPlugin plugin;

    private final Map<String, List<Consumer<JsonObject>>> subscribers = new ConcurrentHashMap<>();

    private final String instanceId = UUID.randomUUID().toString();
    private volatile JedisPubSub pubSub;
    private volatile Thread listenerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RedisManager(JavaPlugin plugin, String host, int port, String username, String password) {
        this.plugin = plugin;

        DefaultJedisClientConfig.Builder cfg = DefaultJedisClientConfig.builder();
        if (username != null && !username.isEmpty()) cfg.user(username);
        if (password != null && !password.isEmpty()) cfg.password(password);

        this.jedisPool = new JedisPool(new HostAndPort(host, port), cfg.build());
        RedisAdapterRegistry.registerBuiltins();
    }

    public String getInstanceId() { return instanceId; }

    public synchronized void start() {
        if (running.get()) return;
        String[] chans = subscribers.keySet().toArray(new String[0]);
        if (chans.length == 0) return;
        running.set(true);
        startListenerInternal(chans);
    }

    public synchronized void close() {
        running.set(false);
        try {
            if (pubSub != null) {
                try { pubSub.unsubscribe(); } catch (Exception ignored) {}
            }
            if (listenerThread != null && listenerThread.isAlive()) {
                listenerThread.join(300);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            pubSub = null;
            listenerThread = null;
            try { jedisPool.close(); } catch (Exception ignored) {}
        }
    }

    public synchronized void subscribeRaw(String channel, Consumer<JsonObject> callback) {
        subscribers
                .computeIfAbsent(channel, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(callback);

        if (running.get()) restartListener();
        else start();
    }

    public void publish(String channel, JsonObject json) {
        json.addProperty("__sender", instanceId);
        json.addProperty("ts", System.currentTimeMillis());
        try (Jedis j = jedisPool.getResource()) {
            j.publish(channel, json.toString());
        }
    }

    private void restartListener() {
        running.set(false);
        try {
            if (pubSub != null) {
                try { pubSub.unsubscribe(); } catch (Exception ignored) {}
            }
            if (listenerThread != null && listenerThread.isAlive()) {
                listenerThread.join(300);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            pubSub = null;
            listenerThread = null;
        }

        String[] chans = subscribers.keySet().toArray(new String[0]);
        if (chans.length == 0) return;
        running.set(true);
        startListenerInternal(chans);
    }

    private void startListenerInternal(String[] channels) {
        pubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                JsonObject obj = JsonParser.parseString(message).getAsJsonObject();

                List<Consumer<JsonObject>> regs = subscribers.get(channel);
                if (regs == null) return;

                Consumer<JsonObject>[] copy;
                synchronized (regs) { copy = regs.toArray(new Consumer[0]); }

                for (Consumer<JsonObject> cb : copy) {
                    try { cb.accept(obj); }
                    catch (Throwable t) { plugin.getLogger().warning("[Redis] Subscriber error: " + t.getMessage()); }
                }
            }
        };

        listenerThread = new Thread(() -> {
            while (running.get()) {
                try (Jedis j = jedisPool.getResource()) {
                    j.subscribe(pubSub, channels);
                } catch (Exception e) {
                    if (!running.get()) break;
                    plugin.getLogger().warning("[Redis] Listener crashed: " + e.getMessage());
                    try { TimeUnit.SECONDS.sleep(2); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); break; }
                }
            }
        }, "RedisListenerThread");

        listenerThread.setDaemon(true);
        listenerThread.start();
    }
}
