package fr.nivcoo.utilsz.redis;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.nivcoo.utilsz.redis.rpc.RedisRpcBus;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class RedisManager {

    private final JedisPool jedisPool;
    private final JavaPlugin plugin;
    private final RedisDispatcher dispatcher;
    private final Map<String, List<RedisListener>> listeners = new HashMap<>();
    private final String instanceId = UUID.randomUUID().toString();

    private JedisPubSub pubSub;
    private Thread listenerThread;

    private volatile boolean running = false;

    public RedisManager(JavaPlugin plugin, String host, int port, String username, String password) {
        this.plugin = plugin;

        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder();
        if (username != null && !username.isEmpty()) builder.user(username);
        if (password != null && !password.isEmpty()) builder.password(password);
        JedisClientConfig config = builder.build();

        this.jedisPool = new JedisPool(new HostAndPort(host, port), config);

        RedisAdapterRegistry.registerBuiltins();
        this.dispatcher = new RedisDispatcher(this);
    }

    public synchronized void start() {
        if (listenerThread != null && listenerThread.isAlive()) return; // déjà lancé
        String[] channels = listeners.keySet().toArray(new String[0]);
        if (channels.length == 0) {
            return;
        }
        running = true;
        startListenerInternal(channels);
    }

    public synchronized void close() {
        running = false;
        try {
            if (pubSub != null) {
                try { pubSub.unsubscribe(); } catch (redis.clients.jedis.exceptions.JedisException ignored) {}
            }
            if (listenerThread != null && listenerThread.isAlive()) {
                listenerThread.join(200);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pubSub = null;
            listenerThread = null;
            try { jedisPool.close(); } catch (Exception ignored) {}
        }
    }

    public String getInstanceId() { return instanceId; }
    public RedisDispatcher getDispatcher() { return dispatcher; }
    public RedisChannelRegistry createRegistry(String channel) { return new RedisChannelRegistry(this, channel); }

    public synchronized void subscribe(String channel, RedisListener listener) {
        listeners.computeIfAbsent(channel, k -> new ArrayList<>()).add(listener);

        if (listenerThread != null && listenerThread.isAlive()) {
            restartListener();
        } else {
            start();
        }
    }

    public void publish(String channel, RedisSerializable message) {
        JsonObject json = dispatcher.serialize(channel, message);
        publish(channel, json);
    }

    public void publish(String channel, JsonObject json) {
        json.addProperty("__sender", instanceId);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.publish(channel, json.toString());
        }
    }

    private void restartListener() {
        running = false;
        try {
            if (pubSub != null) {
                try { pubSub.unsubscribe(); } catch (redis.clients.jedis.exceptions.JedisException ignored) {}
            }
            if (listenerThread != null && listenerThread.isAlive()) {
                listenerThread.join(200);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pubSub = null;
            listenerThread = null;
        }
        String[] channels = listeners.keySet().toArray(new String[0]);
        if (channels.length == 0) return;
        running = true;
        startListenerInternal(channels);
    }

    private void startListenerInternal(String[] channels) {
        pubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                JsonObject obj = JsonParser.parseString(message).getAsJsonObject();

                List<RedisListener> registered = listeners.get(channel);
                if (registered != null) {
                    for (RedisListener l : registered) {
                        if (l.runOnMainThread()) {
                            Bukkit.getScheduler().runTask(plugin, () -> l.onMessage(channel, obj));
                        } else {
                            l.onMessage(channel, obj);
                        }
                    }
                }
                dispatcher.dispatch(channel, obj);
            }
        };

        listenerThread = new Thread(() -> {
            while (running) {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.subscribe(pubSub, channels);
                } catch (Exception e) {
                    if (!running) break;
                    plugin.getLogger().warning("[Redis] Listener crashed: " + e.getMessage());
                    try { TimeUnit.SECONDS.sleep(3); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }, "RedisListenerThread");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public RedisRpcBus createRpcBus(String channel) {
        return new RedisRpcBus(this.plugin, this, channel);
    }
}
