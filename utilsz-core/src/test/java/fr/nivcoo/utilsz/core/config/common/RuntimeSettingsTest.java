package fr.nivcoo.utilsz.core.config.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RuntimeSettingsTest {

    @TempDir
    Path tempDir;

    @Test
    void sqliteSettingsIgnoreInactiveNetworkValuesAndNormalizeThePath() {
        DatabaseConfig first = new DatabaseConfig();
        DatabaseConfig second = new DatabaseConfig();
        first.sqlite.path = "data/../auction.db";
        second.sqlite.path = "auction.db";
        second.mysql.host = "another-host";

        assertEquals(
                first.runtimeSettings(tempDir.toFile()),
                second.runtimeSettings(tempDir.toFile())
        );
    }

    @Test
    void networkSettingsIgnoreSqliteAndTrackTheSelectedDriver() {
        DatabaseConfig first = networkDatabase("mariadb");
        DatabaseConfig second = networkDatabase("mariadb");
        second.sqlite.path = "another.db";

        assertEquals(
                first.runtimeSettings(tempDir.toFile()),
                second.runtimeSettings(tempDir.toFile())
        );

        second.mysql.host = "another-host";
        assertNotEquals(
                first.runtimeSettings(tempDir.toFile()),
                second.runtimeSettings(tempDir.toFile())
        );

        second.mysql.host = first.mysql.host;
        second.type = "mysql";
        assertNotEquals(
                first.runtimeSettings(tempDir.toFile()),
                second.runtimeSettings(tempDir.toFile())
        );
    }

    @Test
    void disabledMessagingIgnoresDormantSettings() {
        MessagingConfig first = new MessagingConfig();
        MessagingConfig second = new MessagingConfig();
        second.driver = "rabbit";
        second.channel = "another-channel";
        second.redis.host = "another-redis";
        second.encryption.enabled = true;

        assertEquals(first.runtimeSettings(), second.runtimeSettings());
    }

    @Test
    void enabledMessagingTracksOnlyTheSelectedBackendAndActiveEncryption() {
        MessagingConfig first = enabledMessaging("redis");
        MessagingConfig second = enabledMessaging("redis");
        second.rabbit.host = "another-rabbit";
        second.encryption.key = "another-inactive-key";

        assertEquals(first.runtimeSettings(), second.runtimeSettings());

        second.redis.host = "another-redis";
        assertNotEquals(first.runtimeSettings(), second.runtimeSettings());

        second.redis.host = first.redis.host;
        first.encryption.enabled = true;
        second.encryption.enabled = true;
        second.encryption.key = "another-active-key";
        assertNotEquals(first.runtimeSettings(), second.runtimeSettings());
    }

    @Test
    void rabbitAliasesProduceTheSameRuntimeSettings() {
        MessagingConfig first = enabledMessaging("rabbit");
        MessagingConfig second = enabledMessaging("rabbitmq");

        assertEquals(first.runtimeSettings(), second.runtimeSettings());
    }

    private DatabaseConfig networkDatabase(String type) {
        DatabaseConfig config = new DatabaseConfig();
        config.type = type;
        return config;
    }

    private MessagingConfig enabledMessaging(String driver) {
        MessagingConfig config = new MessagingConfig();
        config.enabled = true;
        config.driver = driver;
        return config;
    }
}
