package fr.nivcoo.utilsz.core.database.providers;

import fr.nivcoo.utilsz.core.database.ColumnDefinition;
import fr.nivcoo.utilsz.core.database.ColumnType;
import fr.nivcoo.utilsz.core.database.DatabaseProvider;
import fr.nivcoo.utilsz.core.database.TableConstraintDefinition;
import fr.nivcoo.utilsz.core.database.TypedColumnDefinition;

import java.sql.*;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class SQLiteProvider implements DatabaseProvider {

    private static final int BUSY_TIMEOUT_MILLIS = 10_000;

    private final Object lifecycleLock = new Object();
    private final String jdbcUrl;
    private final boolean transientDatabase;
    private final boolean walEligible;
    private volatile boolean connected;
    private Connection keeperConnection;

    public SQLiteProvider(String sqlitePath) {
        String path = Objects.requireNonNull(sqlitePath, "sqlitePath");
        boolean privateTransient = path.isBlank() || path.equalsIgnoreCase(":memory:");
        transientDatabase = privateTransient || isMemoryPath(path);
        jdbcUrl = privateTransient
                ? "jdbc:sqlite:file:utilsz-" + UUID.randomUUID() + "?mode=memory&cache=shared"
                : "jdbc:sqlite:" + sharedMemoryPath(path);
        walEligible = !transientDatabase && !isReadOnlyPath(path);
    }

    @Override
    public void connect() throws SQLException {
        synchronized (lifecycleLock) {
            if (connected) return;

            Connection connection = openConnection();
            try {
                if (walEligible) enableWal(connection);
                if (transientDatabase) {
                    keeperConnection = connection;
                } else {
                    connection.close();
                }
                connected = true;
            } catch (SQLException exception) {
                closeQuietly(connection);
                throw exception;
            }
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        synchronized (lifecycleLock) {
            if (!connected) {
                throw new SQLException("SQLite provider is closed.");
            }
            return openConnection();
        }
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            connected = false;
            closeQuietly(keeperConnection);
            keeperConnection = null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
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
            case DOUBLE, FLOAT -> "REAL";
            case BLOB -> "BLOB";
        };
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MILLIS);
            return connection;
        } catch (SQLException exception) {
            closeQuietly(connection);
            throw exception;
        }
    }

    private void enableWal(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet ignored = statement.executeQuery("PRAGMA journal_mode = WAL")) {
        }
    }

    private static boolean isMemoryPath(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.contains(":memory:") || normalized.contains("mode=memory");
    }

    private static boolean isReadOnlyPath(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.contains("mode=ro") || normalized.contains("immutable=1");
    }

    private static String sharedMemoryPath(String path) {
        if (!isMemoryPath(path) || path.toLowerCase(Locale.ROOT).contains("cache=shared")) {
            return path;
        }
        return path + (path.contains("?") ? "&" : "?") + "cache=shared";
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
