package fr.nivcoo.utilsz.database.providers;

import fr.nivcoo.utilsz.database.ColumnDefinition;
import fr.nivcoo.utilsz.database.DatabaseProvider;
import fr.nivcoo.utilsz.database.TableConstraintDefinition;

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

            if (element instanceof ColumnDefinition col) {
                query.append("`").append(col.name()).append("` ").append(mapType(col.type()));
                if (col.constraints() != null && !col.constraints().isEmpty()) {
                    query.append(" ").append(col.constraints());
                }
            } else if (element instanceof TableConstraintDefinition constraint) {
                query.append(constraint.constraint());
            } else {
                throw new IllegalArgumentException("Unknown table element: " + element.getClass());
            }

            if (i < elements.size() - 1) query.append(", ");
        }

        query.append(");");
        executeUpdate(query.toString());
    }

    private String mapType(String type) {
        return switch (type.toUpperCase()) {
            case "TEXT" -> "TEXT";
            case "INTEGER" -> "INTEGER";
            case "REAL" -> "REAL";
            case "BLOB" -> "BLOB";
            default -> type;
        };
    }
}
