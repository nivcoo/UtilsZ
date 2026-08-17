package fr.nivcoo.utilsz.core.database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public class DatabaseManager {

    private static final long MIN_TRANSACTION_RETRY_MILLIS = 2L;
    private static final long MAX_TRANSACTION_RETRY_MILLIS = 100L;

    private final DatabaseProvider provider;
    private final DatabaseType type;
    private volatile int operationTimeoutSeconds;

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
        Connection connection = provider.getConnection();
        int timeout = operationTimeoutSeconds;
        if (timeout > 0) {
            try {
                connection.setNetworkTimeout(
                        ForkJoinPool.commonPool(), Math.toIntExact(TimeUnit.SECONDS.toMillis(timeout)));
            } catch (SQLFeatureNotSupportedException ignored) {
            }
        }
        return connection;
    }

    public void setOperationTimeoutSeconds(int seconds) {
        if (seconds < 0 || TimeUnit.SECONDS.toMillis(seconds) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Operation timeout is outside the supported range.");
        }
        operationTimeoutSeconds = seconds;
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

    public long currentTimeMillis() throws SQLException {
        try (Connection connection = getConnection()) {
            return currentTimeMillis(connection);
        }
    }

    public long currentTimeMillis(Connection connection) throws SQLException {
        if (connection == null) return currentTimeMillis();

        try (PreparedStatement statement = prepare(connection, currentTimeMillisQuery(type));
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("Database did not return its current time");
            }
            long currentTimeMillis = result.getLong(1);
            if (result.wasNull()) {
                throw new SQLException("Database returned a null current time");
            }
            return currentTimeMillis;
        }
    }

    static String currentTimeMillisQuery(DatabaseType type) {
        return switch (Objects.requireNonNull(type, "type")) {
            case SQLITE -> "SELECT CAST(ROUND((julianday('now') - 2440587.5) * 86400000) AS INTEGER)";
            case MYSQL, MARIADB -> "SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS SIGNED)";
        };
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
             PreparedStatement statement = prepare(connection, query)) {
            bind(statement, params);
            return statement.executeUpdate();
        }
    }

    public <T> T transaction(SqlTransaction<T> transaction) throws SQLException {
        Objects.requireNonNull(transaction, "transaction");
        try {
            return executeTransaction(null, transaction);
        } catch (CommittedTransactionException exception) {
            throw exception.failure();
        }
    }

    public <T> T transaction(TransactionOptions options, SqlTransaction<T> transaction) throws SQLException {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(transaction, "transaction");
        for (int attempt = 1; ; attempt++) {
            try {
                return executeTransaction(options, transaction);
            } catch (CommittedTransactionException exception) {
                throw exception.failure();
            } catch (SQLException exception) {
                if (attempt >= options.maxAttempts()
                        || !isTransientTransactionConflict(type, exception)) {
                    throw exception;
                }
                awaitTransactionRetry(attempt, exception);
            }
        }
    }

    private <T> T executeTransaction(
            TransactionOptions options,
            SqlTransaction<T> transaction
    ) throws SQLException {
        boolean committed = false;
        try (Connection connection = getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            int isolationLevel = connection.getTransactionIsolation();
            Throwable failure = null;
            try {
                if (options != null && isolationLevel != options.isolationLevel()) {
                    connection.setTransactionIsolation(options.isolationLevel());
                }
                if (autoCommit) connection.setAutoCommit(false);
                T result = transaction.execute(connection);
                connection.commit();
                committed = true;
                return result;
            } catch (SQLException | RuntimeException | Error exception) {
                failure = exception;
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreTransactionState(connection, autoCommit, isolationLevel, failure);
            }
        } catch (SQLException exception) {
            if (committed) throw new CommittedTransactionException(exception);
            throw exception;
        }
    }

    public int execute(Connection connection, String query, Object... params) throws SQLException {
        if (connection == null) return execute(query, params);
        try (PreparedStatement statement = prepare(connection, query)) {
            bind(statement, params);
            return statement.executeUpdate();
        }
    }

    public <T> List<T> query(String query, RowMapper<T> mapper, Object... params) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = prepare(connection, query)) {
            return query(statement, mapper, params);
        }
    }

    public <T> List<T> query(Connection connection, String query, RowMapper<T> mapper, Object... params) throws SQLException {
        if (connection == null) return query(query, mapper, params);
        try (PreparedStatement statement = prepare(connection, query)) {
            return query(statement, mapper, params);
        }
    }

    <T> List<T> queryModel(Connection connection, String query, ModelSchema<?> schema,
                           RowMapper<T> mapper, Object... params) throws SQLException {
        if (connection == null) {
            try (Connection ownedConnection = getConnection();
                 PreparedStatement statement = prepare(ownedConnection, query)) {
                return query(statement, mapper, schema, params);
            }
        }
        try (PreparedStatement statement = prepare(connection, query)) {
            return query(statement, mapper, schema, params);
        }
    }

    private <T> List<T> query(PreparedStatement statement, RowMapper<T> mapper, Object... params) throws SQLException {
        return query(statement, mapper, null, params);
    }

    private <T> List<T> query(PreparedStatement statement, RowMapper<T> mapper, ModelSchema<?> schema,
                              Object... params) throws SQLException {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapper.map(DatabaseRow.from(rs, schema)));
                }
                return out;
            }
    }

    public <T> Optional<T> queryOne(String query, RowMapper<T> mapper, Object... params) throws SQLException {
        List<T> rows = query(query, mapper, params);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.getFirst());
    }

    public <T> Optional<T> queryOne(Connection connection, String query, RowMapper<T> mapper, Object... params) throws SQLException {
        List<T> rows = query(connection, query, mapper, params);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.getFirst());
    }

    public int insert(String table, Map<String, ?> values) throws SQLException {
        return insert(null, table, values);
    }

    public int insert(Connection connection, String table, Map<String, ?> values) throws SQLException {
        InsertQuery insert = insertQuery(table, values);
        return connection == null ? execute(insert.sql(), insert.params()) : execute(connection, insert.sql(), insert.params());
    }

    public boolean insertIfAbsent(String table, Map<String, ?> values) throws SQLException {
        return insertIfAbsent(null, table, values);
    }

    public boolean insertIfAbsent(Connection connection, String table, Map<String, ?> values) throws SQLException {
        InsertQuery insert = insertQuery(table, values);
        try {
            int affectedRows = connection == null
                    ? execute(insert.sql(), insert.params())
                    : execute(connection, insert.sql(), insert.params());
            return affectedRows > 0;
        } catch (SQLException exception) {
            if (isDuplicateKey(type, exception)) return false;
            throw exception;
        }
    }

    public long insertReturningId(String table, Map<String, ?> values) throws SQLException {
        try (Connection connection = getConnection()) {
            return insertReturningId(connection, table, values);
        }
    }

    public long insertReturningId(Connection connection, String table, Map<String, ?> values) throws SQLException {
        if (connection == null) return insertReturningId(table, values);

        InsertQuery insert = insertQuery(table, values);
        try (PreparedStatement statement = prepare(connection, insert.sql(), Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, insert.params());
            if (statement.executeUpdate() < 1) {
                throw new SQLException("Insert did not affect any row in table " + table);
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Database did not return a generated id for table " + table);
                }
                long id = keys.getLong(1);
                if (keys.wasNull()) {
                    throw new SQLException("Database returned a null generated id for table " + table);
                }
                return id;
            }
        }
    }

    private InsertQuery insertQuery(String table, Map<String, ?> values) {
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

        String sql = "INSERT INTO " + quote(table) + "(" + columns + ") VALUES (" + placeholders + ")";
        return new InsertQuery(sql, params.toArray());
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
        if (connection == null) return update(table, values, where, params);
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

    public int updateAtomic(String table, Map<String, ? extends AtomicUpdate> updates,
                            String where, Object... params) throws SQLException {
        return updateAtomic(null, table, updates, where, params);
    }

    public int updateAtomic(Connection connection, String table, Map<String, ? extends AtomicUpdate> updates,
                            String where, Object... params) throws SQLException {
        AtomicUpdateQuery update = atomicUpdateQuery(table, updates, where, params);
        return connection == null
                ? execute(update.sql(), update.params())
                : execute(connection, update.sql(), update.params());
    }

    private AtomicUpdateQuery atomicUpdateQuery(String table, Map<String, ? extends AtomicUpdate> updates,
                                                String where, Object... params) {
        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("Cannot perform an empty atomic update.");
        }

        StringJoiner assignments = new StringJoiner(", ");
        List<Object> allParams = new ArrayList<>();
        for (Map.Entry<String, ? extends AtomicUpdate> entry : updates.entrySet()) {
            String column = quote(entry.getKey());
            AtomicUpdate atomicUpdate = Objects.requireNonNull(
                    entry.getValue(), "Atomic update for column " + entry.getKey());
            switch (atomicUpdate) {
                case AtomicUpdate.Set set -> {
                    assignments.add(column + " = ?");
                    allParams.add(set.value());
                }
                case AtomicUpdate.Add add -> {
                    assignments.add(column + " = " + column + " + ?");
                    allParams.add(add.value());
                }
                case AtomicUpdate.Max max -> {
                    assignments.add(column + " = CASE WHEN " + column + " < ? THEN ? ELSE " + column + " END");
                    allParams.add(max.value());
                    allParams.add(max.value());
                }
            }
        }
        if (params != null) {
            for (Object param : params) {
                allParams.add(param);
            }
        }

        String sql = "UPDATE " + quote(table) + " SET " + assignments;
        if (where != null && !where.isBlank()) {
            sql += " WHERE " + where;
        }
        return new AtomicUpdateQuery(sql, allParams.toArray());
    }

    public int delete(String table, String where, Object... params) throws SQLException {
        return delete(null, table, where, params);
    }

    public int delete(Connection connection, String table, String where, Object... params) throws SQLException {
        String sql = "DELETE FROM " + quote(table);
        if (where != null && !where.isBlank()) {
            sql += " WHERE " + where;
        }
        return connection == null ? execute(sql, params) : execute(connection, sql, params);
    }

    public boolean exists(String table, String where, Object... params) throws SQLException {
        return exists(null, table, where, params);
    }

    public boolean exists(Connection connection, String table, String where, Object... params) throws SQLException {
        String sql = "SELECT 1 FROM " + quote(table);
        if (where != null && !where.isBlank()) {
            sql += " WHERE " + where;
        }
        sql += " LIMIT 1";
        return queryOne(connection, sql, row -> 1, params).isPresent();
    }

    public void createIndexIfAbsent(String table, String index, List<String> columns) throws SQLException {
        if (indexExists(table, index)) return;

        StringJoiner joiner = new StringJoiner(", ");
        for (String column : columns) {
            joiner.add(quote(column));
        }
        try {
            executeUpdate("CREATE INDEX " + quote(index) + " ON " + quote(table) + "(" + joiner + ")");
        } catch (SQLException creationFailure) {
            try {
                if (indexExists(table, index)) return;
            } catch (SQLException verificationFailure) {
                creationFailure.addSuppressed(verificationFailure);
            }
            throw creationFailure;
        }
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

    static String quote(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier cannot be blank.");
        }
        String clean = identifier.replace("`", "");
        return "`" + clean + "`";
    }

    static boolean isDuplicateKey(DatabaseType type, SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            int errorCode = current.getErrorCode();
            if ((type == DatabaseType.MYSQL || type == DatabaseType.MARIADB) && errorCode == 1062) {
                return true;
            }
            if (type == DatabaseType.SQLITE) {
                if (errorCode == 1555 || errorCode == 2067 || errorCode == 2579) {
                    return true;
                }
                String message = current.getMessage();
                if (message == null) continue;
                String normalized = message.toUpperCase(Locale.ROOT);
                if (normalized.contains("SQLITE_CONSTRAINT_PRIMARYKEY")
                        || normalized.contains("SQLITE_CONSTRAINT_UNIQUE")
                        || normalized.contains("UNIQUE CONSTRAINT FAILED")
                        || normalized.contains("PRIMARY KEY CONSTRAINT FAILED")) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isTransientTransactionConflict(DatabaseType type, SQLException exception) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(exception, "exception");
        Throwable cause = exception;
        for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
            if (!(cause instanceof SQLException sqlException)) continue;
            SQLException current = sqlException;
            for (int next = 0; current != null && next < 32; next++, current = current.getNextException()) {
                if ("40001".equals(current.getSQLState())) return true;
                int errorCode = current.getErrorCode();
                if ((type == DatabaseType.MYSQL || type == DatabaseType.MARIADB)
                        && (errorCode == 1205 || errorCode == 1213)) {
                    return true;
                }
                if (type != DatabaseType.SQLITE) continue;
                int primaryCode = errorCode & 0xFF;
                if (primaryCode == 5 || primaryCode == 6) return true;
                String message = current.getMessage();
                if (message == null) continue;
                String normalized = message.toUpperCase(Locale.ROOT);
                if (normalized.contains("SQLITE_BUSY")
                        || normalized.contains("SQLITE_LOCKED")
                        || normalized.contains("DATABASE IS LOCKED")
                        || normalized.contains("DATABASE TABLE IS LOCKED")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreTransactionState(
            Connection connection,
            boolean autoCommit,
            int isolationLevel,
            Throwable failure
    ) throws SQLException {
        SQLException restorationFailure = null;
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException exception) {
            restorationFailure = exception;
        }
        try {
            connection.setTransactionIsolation(isolationLevel);
        } catch (SQLException exception) {
            if (restorationFailure == null) restorationFailure = exception;
            else restorationFailure.addSuppressed(exception);
        }
        if (restorationFailure == null) return;
        if (failure == null) throw restorationFailure;
        failure.addSuppressed(restorationFailure);
    }

    private static void awaitTransactionRetry(int failedAttempt, SQLException failure) throws SQLException {
        int shift = Math.min(6, Math.max(0, failedAttempt - 1));
        long delayMillis = Math.min(MAX_TRANSACTION_RETRY_MILLIS,
                MIN_TRANSACTION_RETRY_MILLIS << shift);
        try {
            TimeUnit.MILLISECONDS.sleep(delayMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            SQLException exception = new SQLException(
                    "Interrupted while retrying database transaction", "57014", interrupted);
            exception.addSuppressed(failure);
            throw exception;
        }
    }

    private PreparedStatement prepare(Connection connection, String query) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(query);
        int timeout = operationTimeoutSeconds;
        if (timeout > 0) statement.setQueryTimeout(timeout);
        return statement;
    }

    private PreparedStatement prepare(Connection connection, String query, int generatedKeys) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(query, generatedKeys);
        int timeout = operationTimeoutSeconds;
        if (timeout > 0) statement.setQueryTimeout(timeout);
        return statement;
    }

    private record InsertQuery(String sql, Object[] params) {
    }

    private record AtomicUpdateQuery(String sql, Object[] params) {
    }

    private static final class CommittedTransactionException extends SQLException {
        private final SQLException failure;

        private CommittedTransactionException(SQLException failure) {
            super(failure.getMessage(), failure.getSQLState(), failure.getErrorCode(), failure);
            this.failure = failure;
        }

        private SQLException failure() {
            return failure;
        }
    }

    @FunctionalInterface
    public interface SqlTransaction<T> {
        T execute(Connection connection) throws SQLException;
    }
}
