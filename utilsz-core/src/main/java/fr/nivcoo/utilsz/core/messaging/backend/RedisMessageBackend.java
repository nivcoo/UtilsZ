package fr.nivcoo.utilsz.core.messaging.backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.nivcoo.utilsz.core.messaging.MessageBackend;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class RedisMessageBackend implements MessageBackend {

    private final JedisPool jedisPool;

    private final BackendSubscribers subscribers = new BackendSubscribers();

    private final String instanceId = UUID.randomUUID().toString();
    private volatile JedisPubSub pubSub;
    private volatile Thread listenerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile Consumer<Throwable> errorHandler;

    public RedisMessageBackend(String host, int port, String username, String password) {
        DefaultJedisClientConfig.Builder cfg = DefaultJedisClientConfig.builder();
        if (username != null && !username.isEmpty()) cfg.user(username);
        if (password != null && !password.isEmpty()) cfg.password(password);

        this.jedisPool = new JedisPool(new HostAndPort(host, port), cfg.build());
    }

    @Override
    public void onError(Consumer<Throwable> handler) {
        this.errorHandler = handler;
    }

    private void report(Throwable t) {
        Consumer<Throwable> h = this.errorHandler;
        if (h != null) h.accept(t);
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public synchronized void start() {
        if (running.get()) return;

        String[] chans = subscribers.channels().toArray(new String[0]);
        if (chans.length == 0) return;

        running.set(true);
        startListenerInternal(chans);
    }

    @Override
    public synchronized void close() {
        running.set(false);
        try {
            if (pubSub != null) {
                try {
                    pubSub.unsubscribe();
                } catch (Exception ignored) {
                }
            }
            if (listenerThread != null && listenerThread.isAlive()) {
                listenerThread.join(300);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            pubSub = null;
            listenerThread = null;
            try {
                jedisPool.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public synchronized void subscribeRaw(String channel, Consumer<JsonObject> callback) {
        subscribers.add(channel, callback);

        if (running.get()) restartListener();
    }

    @Override
    public void publish(String channel, JsonObject json) {
        if (!running.get()) {
            throw new IllegalStateException("Redis message backend is not started");
        }
        try (Jedis j = jedisPool.getResource()) {
            j.publish(channel, json == null ? "{}" : json.toString());
        } catch (Exception e) {
            report(e);
            throw new IllegalStateException("Failed to publish Redis message", e);
        }
    }

    private void restartListener() {
        running.set(false);
        try {
            if (pubSub != null) {
                try {
                    pubSub.unsubscribe();
                } catch (Exception ignored) {
                }
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

        String[] chans = subscribers.channels().toArray(new String[0]);
        if (chans.length == 0) return;

        running.set(true);
        startListenerInternal(chans);
    }

    private void startListenerInternal(String[] channels) {
        pubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                JsonObject obj;
                try {
                    obj = JsonParser.parseString(message).getAsJsonObject();
                } catch (Exception e) {
                    report(e);
                    return;
                }

                subscribers.dispatch(channel, obj, RedisMessageBackend.this::report);
            }
        };

        listenerThread = new Thread(() -> {
            while (running.get()) {
                try (Jedis j = jedisPool.getResource()) {
                    j.subscribe(pubSub, channels);
                } catch (Exception e) {
                    if (!running.get()) break;
                    report(e);
                    try {
                        TimeUnit.SECONDS.sleep(2);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "MessagingRedisListener");

        listenerThread.setDaemon(true);
        listenerThread.start();
    }
}
