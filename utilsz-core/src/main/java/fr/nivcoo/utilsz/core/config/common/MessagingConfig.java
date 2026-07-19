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

import java.util.Locale;
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

    public RuntimeSettings runtimeSettings() {
        if (!enabled) {
            return new RuntimeSettings(false, null, null, null, null);
        }

        String resolvedDriver = resolvedDriver();
        return new RuntimeSettings(
                true,
                resolvedDriver,
                channel,
                encryptionSettings(),
                endpointSettings(resolvedDriver)
        );
    }

    private String resolvedDriver() {
        String value = driver == null ? "redis" : driver.toLowerCase(Locale.ROOT);
        return value.equals("rabbitmq") ? "rabbit" : value;
    }

    private EndpointSettings endpointSettings(String resolvedDriver) {
        return switch (resolvedDriver) {
            case "redis" -> new EndpointSettings(
                    redis.host,
                    redis.port,
                    null,
                    redis.username,
                    redis.password
            );
            case "rabbit" -> new EndpointSettings(
                    rabbit.host,
                    rabbit.port,
                    rabbit.virtualHost,
                    rabbit.username,
                    rabbit.password
            );
            default -> null;
        };
    }

    private EncryptionSettings encryptionSettings() {
        if (encryption == null || !encryption.enabled) {
            return new EncryptionSettings(false, null, null, null);
        }
        String algorithm = encryption.algorithm == null
                ? AesGcmMessageCrypto.ALGORITHM
                : encryption.algorithm.trim().toUpperCase(Locale.ROOT);
        return new EncryptionSettings(
                true,
                algorithm,
                encryption.keyId,
                encryption.key
        );
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
        String resolvedDriver = resolvedDriver();
        EndpointSettings endpoint = endpointSettings(resolvedDriver);
        return switch (resolvedDriver) {
            case "redis" -> new RedisMessageBackend(
                    endpoint.host(),
                    endpoint.port(),
                    endpoint.username(),
                    endpoint.password()
            );
            case "rabbit", "rabbitmq" -> new RabbitMqMessageBackend(
                    endpoint.host(),
                    endpoint.port(),
                    endpoint.virtualHost(),
                    endpoint.username(),
                    endpoint.password()
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
        EncryptionSettings settings = encryptionSettings();
        if (!settings.enabled()) {
            return NoopMessageCrypto.INSTANCE;
        }

        String resolvedAlgorithm = settings.algorithm();
        if (!AesGcmMessageCrypto.ALGORITHM.equalsIgnoreCase(resolvedAlgorithm)) {
            if (logger != null) {
                logger.warn("Messaging encryption algorithm inconnu: {}", resolvedAlgorithm);
            }
            return null;
        }

        try {
            return new AesGcmMessageCrypto(
                    MessageCryptoKeys.decode(settings.key()),
                    settings.keyId()
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
        @Comment("Algorithme de chiffrement. Optionnel: laissez vide pour utiliser AES-256-GCM.")
        public String algorithm = "AES-256-GCM";
        @Optional
        public String keyId = "default";
        @Comment("Clé brute partagée entre tous les services qui chiffrent ce channel. AES-256-GCM exige exactement 32 octets UTF-8. Supprimez la ligne et une nouvelle clé sera générée au start du serveur.")
        public String key = MessageCryptoKeys.generate();
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

    public record RuntimeSettings(
            boolean enabled,
            String driver,
            String channel,
            EncryptionSettings encryption,
            EndpointSettings endpoint
    ) {
    }

    public record EncryptionSettings(
            boolean enabled,
            String algorithm,
            String keyId,
            String key
    ) {
    }

    public record EndpointSettings(
            String host,
            int port,
            String virtualHost,
            String username,
            String password
    ) {
    }
}
