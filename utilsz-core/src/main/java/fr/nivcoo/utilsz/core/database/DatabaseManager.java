package fr.nivcoo.utilsz.core.database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

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

    public DatabaseTable table(String tableName) {
        return new DatabaseTable(this, tableName);
    }

    public <T> ModelRepository<T> model(DatabaseModel<T> model) {
        return new ModelRepository<>(this, Objects.requireNonNull(model, "model"));
    }

    public int execute(String query, Object... params) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            bind(statement, params);
            return statement.executeUpdate();
        }
    }

    public <T> T transaction(SqlTransaction<T> transaction) throws SQLException {
        Objects.requireNonNull(transaction, "transaction");
        try (Connection connection = getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = transaction.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    public int execute(Connection connection, String query, Object... params) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            bind(statement, params);
            return statement.executeUpdate();
        }
    }

    public <T> List<T> query(String query, RowMapper<T> mapper, Object... params) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapper.map(DatabaseRow.from(rs)));
                }
                return out;
            }
        }
    }

    public <T> Optional<T> queryOne(String query, RowMapper<T> mapper, Object... params) throws SQLException {
        List<T> rows = query(query, mapper, params);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.getFirst());
    }

    public int insert(String table, Map<String, ?> values) throws SQLException {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Cannot insert an empty value map.");
        }

        StringJoiner columns = new StringJoiner(", ");
        StringJoiner placeholders = new StringJoiner(", ");
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            columns.add(quote(entry.getKey()));
            placeholders.add("?");
            params.add(entry.getValue());
        }

        return execute("INSERT INTO " + quote(table) + "(" + columns + ") VALUES (" + placeholders + ")",
                params.toArray());
    }

    public int update(String table, Map<String, ?> values, String where, Object... params) throws SQLException {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Cannot update an empty value map.");
        }

        StringJoiner assignments = new StringJoiner(", ");
        List<Object> allParams = new ArrayList<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            assignments.add(quote(entry.getKey()) + " = ?");
            allParams.add(entry.getValue());
        }
        if (params != null) {
            allParams.addAll(List.of(params));
        }

        String sql = "UPDATE " + quote(table) + " SET " + assignments;
        if (where != null && !where.isBlank()) {
            sql += " WHERE " + where;
        }
        return execute(sql, allParams.toArray());
    }

    public int update(Connection connection, String table, Map<String, ?> values, String where, Object... params) throws SQLException {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Cannot update an empty value map.");
        }

        StringJoiner assignments = new StringJoiner(", ");
        List<Object> allParams = new ArrayList<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            assignments.add(quote(entry.getKey()) + " = ?");
            allParams.add(entry.getValue());
        }
        if (params != null) {
            allParams.addAll(List.of(params));
        }

        String sql = "UPDATE " + quote(table) + " SET " + assignments;
        if (where != null && !where.isBlank()) {
            sql += " WHERE " + where;
        }
        return execute(connection, sql, allParams.toArray());
    }

    public int delete(String table, String where, Object... params) throws SQLException {
        String sql = "DELETE FROM " + quote(table);
        if (where != null && !where.isBlank()) {
            sql += " WHERE " + where;
        }
        return execute(sql, params);
    }

    public boolean exists(String table, String where, Object... params) throws SQLException {
        String sql = "SELECT 1 FROM " + quote(table);
        if (where != null && !where.isBlank()) {
            sql += " WHERE " + where;
        }
        sql += " LIMIT 1";
        return queryOne(sql, row -> 1, params).isPresent();
    }

    public void createIndexIfAbsent(String table, String index, List<String> columns) throws SQLException {
        if (indexExists(table, index)) return;

        StringJoiner joiner = new StringJoiner(", ");
        for (String column : columns) {
            joiner.add(quote(column));
        }
        executeUpdate("CREATE INDEX " + quote(index) + " ON " + quote(table) + "(" + joiner + ")");
    }

    public boolean indexExists(String table, String index) throws SQLException {
        try (Connection connection = getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getIndexInfo(null, null, table, false, false)) {
                while (rs.next()) {
                    if (index.equalsIgnoreCase(rs.getString("INDEX_NAME"))) return true;
                }
            }
            try (ResultSet rs = meta.getIndexInfo(null, null, table.toUpperCase(), false, false)) {
                while (rs.next()) {
                    if (index.equalsIgnoreCase(rs.getString("INDEX_NAME"))) return true;
                }
            }
        }
        return false;
    }

    private void bind(PreparedStatement statement, Object... params) throws SQLException {
        if (params == null) return;
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, DatabaseCodecs.encode(params[i]));
        }
    }

    private String quote(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier cannot be blank.");
        }
        String clean = identifier.replace("`", "");
        return "`" + clean + "`";
    }

    @FunctionalInterface
    public interface SqlTransaction<T> {
        T execute(Connection connection) throws SQLException;
    }
}
