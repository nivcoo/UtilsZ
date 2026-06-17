package fr.nivcoo.utilsz.core.database.providers;

import fr.nivcoo.utilsz.core.database.ColumnDefinition;
import fr.nivcoo.utilsz.core.database.ColumnType;
import fr.nivcoo.utilsz.core.database.DatabaseProvider;
import fr.nivcoo.utilsz.core.database.TableConstraintDefinition;
import fr.nivcoo.utilsz.core.database.TypedColumnDefinition;

import java.sql.*;
import java.util.List;

public class SQLiteProvider implements DatabaseProvider {

    private final String sqlitePath;
    private Connection connection;

    public SQLiteProvider(String sqlitePath) {
        this.sqlitePath = sqlitePath;
    }

    @Override
    public void connect() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connect();
        }
        return connection;
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
        }
    }

    @Override
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void executeUpdate(String query) throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(query);
        }
    }

    @Override
    public void executeBatch(List<String> queries) throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            for (String query : queries) {
                stmt.addBatch(query);
            }
            stmt.executeBatch();
        }
    }

    @Override
    public void createTable(String tableName, List<Object> elements) throws SQLException {
        StringBuilder query = new StringBuilder("CREATE TABLE IF NOT EXISTS `" + tableName + "` (");

        for (int i = 0; i < elements.size(); i++) {
            Object element = elements.get(i);

            switch (element) {
                case ColumnDefinition(String name, String type, String constraints) -> {
                    query.append("`").append(name).append("` ").append(type);
                    if (constraints != null && !constraints.isEmpty()) {
                        query.append(" ").append(constraints);
                    }
                }
                case TypedColumnDefinition(String name, ColumnType type, int length, String constraints) -> {
                    query.append("`").append(name).append("` ").append(mapType(type, length));
                    if (constraints != null && !constraints.isEmpty()) {
                        query.append(" ").append(constraints);
                    }
                }
                case TableConstraintDefinition(String constraint1) -> query.append(constraint1);
                default -> throw new IllegalArgumentException("Unknown table element: " + element.getClass());
            }

            if (i < elements.size() - 1) query.append(", ");
        }

        query.append(");");
        executeUpdate(query.toString());
    }

    private String mapType(ColumnType type, int length) {
        return switch (type) {
            case ID, INT, BOOLEAN -> "INTEGER";
            case UUID -> "VARCHAR(36)";
            case STRING -> "VARCHAR(" + (length > 0 ? length : 255) + ")";
            case TEXT -> "TEXT";
            case LONG -> "BIGINT";
            case DECIMAL -> "VARCHAR(48)";
            case BLOB -> "BLOB";
        };
    }
}
