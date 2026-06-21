package fr.nivcoo.utilsz.core.config.common;

import fr.nivcoo.utilsz.core.config.annotations.Comment;
import fr.nivcoo.utilsz.core.config.annotations.Section;
import fr.nivcoo.utilsz.core.messaging.DefaultMessageBus;
import fr.nivcoo.utilsz.core.messaging.MessageBackend;
import fr.nivcoo.utilsz.core.messaging.MessageBus;
import fr.nivcoo.utilsz.core.messaging.NoopMessageBus;
import fr.nivcoo.utilsz.core.messaging.backend.RabbitMqMessageBackend;
import fr.nivcoo.utilsz.core.messaging.backend.RedisMessageBackend;
import org.slf4j.Logger;

import java.util.function.Consumer;

@Section
public class MessagingConfig {

    public boolean enabled = false;
    @Comment("redis ou rabbit")
    public String driver = "redis";
    public String channel = "plugin";
    public Redis redis = new Redis();
    public Rabbit rabbit = new Rabbit();

    public MessagingConfig() {
    }

    public MessagingConfig(boolean enabled, String channel) {
        this.enabled = enabled;
        this.channel = channel;
    }

    public MessageBus createBus(Consumer<Runnable> mainThreadExecutor, Logger logger) {
        if (!enabled) {
            return new NoopMessageBus();
        }

        MessageBackend backend = createBackend(logger);
        if (backend == null) {
            return new NoopMessageBus();
        }
        return new DefaultMessageBus(backend, channel, mainThreadExecutor, logger);
    }

    public MessageBackend createBackend(Logger logger) {
        String resolvedDriver = driver == null ? "redis" : driver.toLowerCase();
        return switch (resolvedDriver) {
            case "redis" -> new RedisMessageBackend(redis.host, redis.port, redis.username, redis.password);
            case "rabbit", "rabbitmq" -> new RabbitMqMessageBackend(
                    rabbit.host,
                    rabbit.port,
                    rabbit.virtualHost,
                    rabbit.username,
                    rabbit.password
            );
            default -> {
                if (logger != null) {
                    logger.warn("Messaging driver inconnu: {}", resolvedDriver);
                }
                yield null;
            }
        };
    }

    @Section
    public static class Redis {
        public String host = "127.0.0.1";
        public int port = 6379;
        public String username = "";
        public String password = "";
    }

    @Section
    public static class Rabbit {
        public String host = "127.0.0.1";
        public int port = 5672;
        public String virtualHost = "/";
        public String username = "guest";
        public String password = "";
    }
}
