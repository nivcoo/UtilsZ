package fr.nivcoo.utilsz.database.providers;

import fr.nivcoo.utilsz.database.ColumnDefinition;
import fr.nivcoo.utilsz.database.DatabaseProvider;
import fr.nivcoo.utilsz.database.TableConstraintDefinition;

import java.sql.*;
import java.util.List;

/**
 * MySQL provider for managing MySQL database connections and operations.
 */
public class MySQLProvider implements DatabaseProvider {
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private Connection connection;

    public MySQLProvider(String host, int port, String database, String username, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    @Override
    public void connect() throws SQLException {
        if (connection == null || connection.isClosed()) {
            String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC", host, port, database);
            connection = DriverManager.getConnection(url, username, password);
        }
    }

    @Override
    public Connection getConnection() {
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
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(query);
        }
    }

    @Override
    public ResultSet executeQuery(String query) throws SQLException {
        Statement statement = connection.createStatement();
        return statement.executeQuery(query);
    }

    @Override
    public void executeBatch(List<String> queries) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String query : queries) {
                statement.addBatch(query);
            }
            statement.executeBatch();
        }
    }

    @Override
    public PreparedStatement prepareStatement(String query) throws SQLException {
        return connection.prepareStatement(query);
    }

    @Override
    public void createTable(String tableName, List<Object> elements) throws SQLException {
        StringBuilder query = new StringBuilder("CREATE TABLE IF NOT EXISTS `" + tableName + "` (");
        for (int i = 0; i < elements.size(); i++) {
            Object element = elements.get(i);

            if (element instanceof ColumnDefinition(String name, String type, String constraints)) {
                query.append("`").append(name).append("` ")
                        .append(mapType(type));
                if (constraints != null && !constraints.isEmpty()) {
                    query.append(" ").append(constraints);
                }
            } else if (element instanceof TableConstraintDefinition constraint) {
                query.append(constraint.getConstraint());
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
            case "TEXT" -> "VARCHAR(255)";
            case "INTEGER" -> "INT";
            case "REAL" -> "DOUBLE";
            case "BLOB" -> "LONGBLOB";
            default -> type;
        };
    }


}
