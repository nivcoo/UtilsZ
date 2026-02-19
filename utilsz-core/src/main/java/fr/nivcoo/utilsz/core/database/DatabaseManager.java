package fr.nivcoo.utilsz.core.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class DatabaseManager {

    private final DatabaseProvider provider;
    private final DatabaseType type;

    public DatabaseManager(DatabaseType type, String host, int port, String database,
                           String username, String password, String sqlitePath) {
        this.type = type;
        this.provider = DatabaseType.getProvider(type, host, port, database, username, password, sqlitePath);
        try {
            this.provider.connect();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to the database.", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return provider.getConnection();
    }

    public void closeConnection() {
        provider.close();
    }

    public DatabaseType getType() {
        return type;
    }

    public boolean isConnected() {
        return provider.isConnected();
    }

    public void executeUpdate(String query) throws SQLException {
        provider.executeUpdate(query);
    }

    public void executeBatch(List<String> queries) throws SQLException {
        provider.executeBatch(queries);
    }

    public void createTable(String tableName, List<Object> columns) throws SQLException {
        provider.createTable(tableName, columns);
    }
}
