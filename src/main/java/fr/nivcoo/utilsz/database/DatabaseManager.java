package fr.nivcoo.utilsz.database;

import java.sql.*;
import java.util.List;

public class DatabaseManager {

    private final DatabaseProvider provider;

    public DatabaseManager(DatabaseType type, String host, int port, String database,
                           String username, String password, String sqlitePath) {
        this.provider = DatabaseType.getProvider(type, host, port, database, username, password, sqlitePath);
        try {
            this.provider.connect();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to the database.", e);
        }
    }

    public Connection getConnection() {
        return provider.getConnection();
    }

    public void closeConnection() {
        provider.close();
    }

    public boolean isConnected() {
        return provider.isConnected();
    }

    public void executeUpdate(String query) throws SQLException {
        provider.executeUpdate(query);
    }

    public ResultSet executeQuery(String query) throws SQLException {
        return provider.executeQuery(query);
    }

    public void executeBatch(List<String> queries) throws SQLException {
        provider.executeBatch(queries);
    }

    public PreparedStatement prepareStatement(String query) throws SQLException {
        return provider.prepareStatement(query);
    }

    public void createTable(String tableName, List<ColumnDefinition> columns) throws SQLException {
        provider.createTable(tableName, columns);
    }
}
