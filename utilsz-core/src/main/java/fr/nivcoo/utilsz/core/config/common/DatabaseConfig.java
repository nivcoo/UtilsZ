package fr.nivcoo.utilsz.core.config.common;

import fr.nivcoo.utilsz.core.config.annotations.Comment;
import fr.nivcoo.utilsz.core.config.annotations.Section;
import fr.nivcoo.utilsz.core.database.DatabaseManager;
import fr.nivcoo.utilsz.core.database.DatabaseType;

import java.io.File;
import java.util.Locale;

@Section
@SuppressWarnings("unused")
public class DatabaseConfig {

    @Comment("sqlite / mysql / mariadb")
    public String type = "sqlite";
    public Sqlite sqlite = new Sqlite();
    public Mysql mysql = new Mysql();

    public DatabaseConfig() {
    }

    public DatabaseConfig(String type, String sqlitePath, String mysqlDatabase, String mysqlUsername) {
        this.type = type;
        this.sqlite.path = sqlitePath;
        this.mysql.database = mysqlDatabase;
        this.mysql.username = mysqlUsername;
    }

    public DatabaseType databaseType() {
        return switch ((type == null ? "sqlite" : type).toLowerCase(Locale.ROOT)) {
            case "mysql" -> DatabaseType.MYSQL;
            case "mariadb" -> DatabaseType.MARIADB;
            default -> DatabaseType.SQLITE;
        };
    }

    public RuntimeSettings runtimeSettings(File dataFolder) {
        DatabaseType resolvedType = databaseType();
        if (resolvedType == DatabaseType.SQLITE) {
            String path = new File(dataFolder, sqlite.path)
                    .getAbsoluteFile()
                    .toPath()
                    .normalize()
                    .toString();
            return new RuntimeSettings(resolvedType, path, null);
        }
        return new RuntimeSettings(resolvedType, null, new NetworkSettings(
                mysql.host,
                mysql.port,
                mysql.database,
                mysql.username,
                mysql.password
        ));
    }

    public DatabaseManager createManager(File dataFolder) {
        RuntimeSettings settings = runtimeSettings(dataFolder);
        NetworkSettings network = settings.network();
        if (network == null) {
            network = new NetworkSettings(null, 0, null, null, null);
        }
        return new DatabaseManager(
                settings.type(),
                network.host(),
                network.port(),
                network.database(),
                network.username(),
                network.password(),
                settings.sqlitePath()
        );
    }

    @Section
    @SuppressWarnings("unused")
    public static class Sqlite {
        @Comment("Chemin SQLite relatif au dossier plugin.")
        public String path = "database.db";
    }

    @Section
    @SuppressWarnings("unused")
    public static class Mysql {
        public String host = "127.0.0.1";
        public int port = 3306;
        public String database = "plugin";
        public String username = "root";
        public String password = "";
    }

    public record RuntimeSettings(
            DatabaseType type,
            String sqlitePath,
            NetworkSettings network
    ) {
    }

    public record NetworkSettings(
            String host,
            int port,
            String database,
            String username,
            String password
    ) {
    }
}
