package fr.nivcoo.utilsz.redis;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    private volatile boolean running = true;

    public RedisManager(JavaPlugin plugin, String host, int port, String username, String password) {
        this.plugin = plugin;

        JedisClientConfig config;
        if ((password != null && !password.isEmpty()) || (username != null && !username.isEmpty())) {
            config = DefaultJedisClientConfig.builder()
                    .user((username == null || username.isEmpty()) ? null : username)
                    .password((password == null || password.isEmpty()) ? null : password)
                    .build();
        } else {
            config = DefaultJedisClientConfig.builder().build();
        }

        this.jedisPool = new JedisPool(new HostAndPort(host, port), config);

        RedisAdapterRegistry.registerBuiltins();

        this.dispatcher = new RedisDispatcher(this);
    }

    public void start() {
        startListener();
    }

    public void close() {
        running = false;
        try {
            if (pubSub != null) pubSub.unsubscribe();
            if (listenerThread != null && listenerThread.isAlive()) {
                listenerThread.join(50);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        jedisPool.close();
    }

    public String getInstanceId() {
        return instanceId;
    }

    public RedisDispatcher getDispatcher() {
        return dispatcher;
    }

    public RedisChannelRegistry createRegistry(String channel) {
        return new RedisChannelRegistry(this, channel);
    }

    public void subscribe(String channel, RedisListener listener) {
        listeners.computeIfAbsent(channel, k -> new ArrayList<>()).add(listener);
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

    private void startListener() {
        if (pubSub != null || listenerThread != null) {
            close();
        }

        pubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                JsonObject obj = JsonParser.parseString(message).getAsJsonObject();

                List<RedisListener> registeredListeners = listeners.get(channel);
                if (registeredListeners != null) {
                    for (RedisListener listener : registeredListeners) {
                        if (listener.runOnMainThread()) {
                            Bukkit.getScheduler().runTask(plugin, () -> listener.onMessage(channel, obj));
                        } else {
                            listener.onMessage(channel, obj);
                        }
                    }
                }

                dispatcher.dispatch(channel, obj);
            }
        };

        listenerThread = new Thread(() -> {
            while (running) {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.subscribe(pubSub, listeners.keySet().toArray(new String[0]));
                } catch (Exception e) {
                    plugin.getLogger().warning("[Redis] Listener crashed: " + e.getMessage());
                    if (!running) break;
                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "RedisListenerThread");
        listenerThread.start();
    }
}
