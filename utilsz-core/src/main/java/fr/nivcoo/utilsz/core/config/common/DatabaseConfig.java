package fr.nivcoo.utilsz.core.config.common;

import fr.nivcoo.utilsz.core.config.annotations.Comment;
import fr.nivcoo.utilsz.core.config.annotations.Section;
import fr.nivcoo.utilsz.core.database.DatabaseManager;
import fr.nivcoo.utilsz.core.database.DatabaseType;

import java.io.File;

@Section
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
        return switch ((type == null ? "sqlite" : type).toLowerCase()) {
            case "mysql" -> DatabaseType.MYSQL;
            case "mariadb" -> DatabaseType.MARIADB;
            default -> DatabaseType.SQLITE;
        };
    }

    public DatabaseManager createManager(File dataFolder) {
        String sqlitePath = new File(dataFolder, sqlite.path).getPath();
        return new DatabaseManager(
                databaseType(),
                mysql.host,
                mysql.port,
                mysql.database,
                mysql.username,
                mysql.password,
                sqlitePath
        );
    }

    @Section
    public static class Sqlite {
        @Comment("Chemin SQLite relatif au dossier plugin.")
        public String path = "database.db";
    }

    @Section
    public static class Mysql {
        public String host = "127.0.0.1";
        public int port = 3306;
        public String database = "plugin";
        public String username = "root";
        public String password = "";
    }
}
