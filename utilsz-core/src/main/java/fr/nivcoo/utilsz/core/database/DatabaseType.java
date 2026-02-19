package fr.nivcoo.utilsz.core.database;

import fr.nivcoo.utilsz.core.database.providers.MariaDBProvider;
import fr.nivcoo.utilsz.core.database.providers.MySQLProvider;
import fr.nivcoo.utilsz.core.database.providers.SQLiteProvider;

/**
 * Enum representing supported database types.
 */
public enum DatabaseType {
    SQLITE,
    MYSQL,
    MARIADB;

    /**
     * Returns the appropriate DatabaseProvider for the given DatabaseType.
     *
     * @param type the database type
     * @param host the host for MYSQL and MARIADB
     * @param port the port for MYSQL and MARIADB
     * @param database the database name for MYSQL and MARIADB
     * @param username the username for MYSQL and MARIADB
     * @param password the password for MYSQL and MARIADB
     * @param sqlitePath the SQLite database file path
     * @return the corresponding DatabaseProvider
     */
    public static DatabaseProvider getProvider(DatabaseType type, String host, int port, String database,
                                               String username, String password, String sqlitePath) {
        return switch (type) {
            case MYSQL -> new MySQLProvider(host, port, database, username, password);
            case MARIADB -> new MariaDBProvider(host, port, database, username, password);
            case SQLITE -> new SQLiteProvider(sqlitePath);
        };
    }
}
