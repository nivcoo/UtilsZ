package fr.nivcoo.utilsz.core.config.common;

import fr.nivcoo.utilsz.core.config.annotations.Comment;
import fr.nivcoo.utilsz.core.config.annotations.Optional;
import fr.nivcoo.utilsz.core.config.annotations.Section;
import fr.nivcoo.utilsz.core.messaging.DefaultMessageBus;
import fr.nivcoo.utilsz.core.messaging.MessageBackend;
import fr.nivcoo.utilsz.core.messaging.MessageBus;
import fr.nivcoo.utilsz.core.messaging.NoopMessageBus;
import fr.nivcoo.utilsz.core.messaging.backend.RabbitMqMessageBackend;
import fr.nivcoo.utilsz.core.messaging.backend.RedisMessageBackend;
import fr.nivcoo.utilsz.core.messaging.crypto.AesGcmMessageCrypto;
import fr.nivcoo.utilsz.core.messaging.crypto.MessageCrypto;
import fr.nivcoo.utilsz.core.messaging.crypto.MessageCryptoKeys;
import fr.nivcoo.utilsz.core.messaging.crypto.NoopMessageCrypto;
import org.slf4j.Logger;

import java.util.function.Consumer;

@Section
@SuppressWarnings("unused")
public class MessagingConfig {

    public boolean enabled = false;
    @Comment("redis ou rabbit")
    public String driver = "redis";
    public String channel = "plugin";
    public Encryption encryption = new Encryption();
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

        MessageCrypto crypto = createCrypto(logger);
        if (crypto == null) {
            return new NoopMessageBus();
        }

        return new DefaultMessageBus(backend, channel, mainThreadExecutor, logger, crypto);
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

    public MessageCrypto createCrypto(Logger logger) {
        if (encryption == null || !encryption.enabled) {
            return NoopMessageCrypto.INSTANCE;
        }

        String resolvedAlgorithm = encryption.algorithm == null
                ? AesGcmMessageCrypto.ALGORITHM
                : encryption.algorithm.trim();
        if (!AesGcmMessageCrypto.ALGORITHM.equalsIgnoreCase(resolvedAlgorithm)) {
            if (logger != null) {
                logger.warn("Messaging encryption algorithm inconnu: {}", resolvedAlgorithm);
            }
            return null;
        }

        try {
            return new AesGcmMessageCrypto(
                    MessageCryptoKeys.decode(encryption.key),
                    encryption.keyId
            );
        } catch (Exception ex) {
            if (logger != null) {
                logger.warn("Messaging encryption invalide: {}", ex.getMessage());
            }
            return null;
        }
    }

    @Section
    @SuppressWarnings("unused")
    public static class Encryption {
        public boolean enabled = false;
        @Optional
        @Comment("AES-256-GCM. La cle doit etre base64 et rester secrete.")
        public String algorithm = "AES-256-GCM";
        @Optional
        public String keyId = "default";
        public String key = "";
    }

    @Section
    @SuppressWarnings("unused")
    public static class Redis {
        public String host = "127.0.0.1";
        public int port = 6379;
        public String username = "";
        public String password = "";
    }

    @Section
    @SuppressWarnings("unused")
    public static class Rabbit {
        public String host = "127.0.0.1";
        public int port = 5672;
        public String virtualHost = "/";
        public String username = "guest";
        public String password = "";
    }
}
