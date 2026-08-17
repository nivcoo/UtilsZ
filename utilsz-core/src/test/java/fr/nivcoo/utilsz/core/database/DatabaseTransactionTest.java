package fr.nivcoo.utilsz.core.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTransactionTest {

    @TempDir
    Path tempDirectory;

    @Test
    void concurrentSerializableTransactionsRespectCounterLimitWithoutLostUpdates() throws Exception {
        DatabaseManager database = database(tempDirectory.resolve("serializable-counter.db"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier firstReads = new CyclicBarrier(2);
        AtomicInteger executions = new AtomicInteger();
        Set<Connection> connections = ConcurrentHashMap.newKeySet();
        try {
            database.execute("CREATE TABLE counters (`id` INTEGER PRIMARY KEY, `value` BIGINT NOT NULL)");
            database.execute("INSERT INTO counters (`id`, `value`) VALUES (?, ?)", 1, 0L);

            Future<Boolean> first = executor.submit(() -> incrementBelowLimit(
                    database, firstReads, executions, connections));
            Future<Boolean> second = executor.submit(() -> incrementBelowLimit(
                    database, firstReads, executions, connections));

            int accepted = (first.get(30, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(30, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, accepted);
            assertEquals(1L, counter(database));
            assertTrue(executions.get() >= 3);
            assertEquals(executions.get(), connections.size());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            database.closeConnection();
        }
    }

    @Test
    void serializableFailureRollsBackAndDoesNotRetryBusinessErrors() throws Exception {
        DatabaseManager database = database(tempDirectory.resolve("serializable-rollback.db"));
        AtomicInteger executions = new AtomicInteger();
        try {
            database.execute("CREATE TABLE counters (`id` INTEGER PRIMARY KEY, `value` BIGINT NOT NULL)");
            database.execute("INSERT INTO counters (`id`, `value`) VALUES (?, ?)", 1, 0L);

            SQLException failure = assertThrows(SQLException.class, () -> database.transaction(
                    TransactionOptions.serializable(4), connection -> {
                        executions.incrementAndGet();
                        database.execute(connection,
                                "UPDATE counters SET `value` = `value` + 1 WHERE `id` = ?", 1);
                        throw new SQLException("rollback", "HY000");
                    }));

            assertEquals("rollback", failure.getMessage());
            assertEquals(1, executions.get());
            assertEquals(0L, counter(database));
        } finally {
            database.closeConnection();
        }
    }

    @Test
    void retriesStopAtConfiguredAttemptLimitAndRestoreConnectionState() throws Exception {
        ConnectionState state = new ConnectionState();
        DatabaseManager database = trackingDatabase(state);
        AtomicInteger executions = new AtomicInteger();
        try {
            SQLException failure = assertThrows(SQLException.class, () -> database.transaction(
                    TransactionOptions.serializable(3), connection -> {
                        executions.incrementAndGet();
                        assertFalse(connection.getAutoCommit());
                        assertEquals(Connection.TRANSACTION_SERIALIZABLE,
                                connection.getTransactionIsolation());
                        throw new SQLException("[SQLITE_BUSY_SNAPSHOT] busy", "HY000", 517);
                    }));

            assertEquals(517, failure.getErrorCode());
            assertEquals(3, executions.get());
            assertEquals(3, state.rollbacks.get());
            assertEquals(0, state.commits.get());
            assertEquals(3, state.closes.get());
            assertTrue(state.autoCommit.get());
            assertEquals(Connection.TRANSACTION_READ_COMMITTED, state.isolation.get());
        } finally {
            database.closeConnection();
        }
    }

    @Test
    void interruptedRetryRestoresInterruptStatusAndStops() {
        ConnectionState state = new ConnectionState();
        DatabaseManager database = trackingDatabase(state);
        AtomicInteger executions = new AtomicInteger();
        try {
            Thread.currentThread().interrupt();
            SQLException failure = assertThrows(SQLException.class, () -> database.transaction(
                    TransactionOptions.serializable(2), connection -> {
                        executions.incrementAndGet();
                        throw new SQLException("busy", "HY000", 5);
                    }));

            assertEquals("57014", failure.getSQLState());
            assertInstanceOf(InterruptedException.class, failure.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, executions.get());
        } finally {
            Thread.interrupted();
            database.closeConnection();
        }
    }

    @Test
    void confirmedCommitIsNotRetriedWhenConnectionCloseFails() {
        ConnectionState state = new ConnectionState();
        state.closeFailure = new SQLException("busy after commit", "HY000", 5);
        DatabaseManager database = trackingDatabase(state);
        AtomicInteger executions = new AtomicInteger();
        try {
            SQLException failure = assertThrows(SQLException.class, () -> database.transaction(
                    TransactionOptions.serializable(3), connection -> executions.incrementAndGet()));

            assertEquals(5, failure.getErrorCode());
            assertEquals(1, executions.get());
            assertEquals(1, state.commits.get());
            assertEquals(0, state.rollbacks.get());
            assertTrue(state.autoCommit.get());
            assertEquals(Connection.TRANSACTION_READ_COMMITTED, state.isolation.get());
        } finally {
            database.closeConnection();
        }
    }

    @Test
    void classifiesOnlySupportedTransientTransactionConflicts() {
        assertTrue(DatabaseManager.isTransientTransactionConflict(DatabaseType.MYSQL,
                new SQLException("deadlock", "41000", 1213)));
        assertTrue(DatabaseManager.isTransientTransactionConflict(DatabaseType.MARIADB,
                new SQLException("timeout", "41000", 1205)));
        assertTrue(DatabaseManager.isTransientTransactionConflict(DatabaseType.MYSQL,
                new SQLException("serialization", "40001", 0)));
        assertTrue(DatabaseManager.isTransientTransactionConflict(DatabaseType.SQLITE,
                new SQLException("busy", null, 5)));
        assertTrue(DatabaseManager.isTransientTransactionConflict(DatabaseType.SQLITE,
                new SQLException("locked", null, 6)));
        assertTrue(DatabaseManager.isTransientTransactionConflict(DatabaseType.SQLITE,
                new SQLException("snapshot", null, 517)));
        assertTrue(DatabaseManager.isTransientTransactionConflict(DatabaseType.SQLITE,
                new SQLException("shared cache", null, 262)));

        SQLException chained = new SQLException("wrapper", "HY000", 0);
        chained.setNextException(new SQLException("deadlock", "40001", 0));
        assertTrue(DatabaseManager.isTransientTransactionConflict(DatabaseType.MYSQL, chained));
        assertTrue(DatabaseManager.isTransientTransactionConflict(DatabaseType.SQLITE,
                new SQLException("wrapper", new SQLException("locked", null, 6))));

        assertFalse(DatabaseManager.isTransientTransactionConflict(DatabaseType.MYSQL,
                new SQLException("duplicate", "23000", 1062)));
        assertFalse(DatabaseManager.isTransientTransactionConflict(DatabaseType.SQLITE,
                new SQLException("constraint", "23000", 19)));
        assertFalse(DatabaseManager.isTransientTransactionConflict(DatabaseType.MYSQL,
                new SQLException("connection", "08006", 0)));
        assertFalse(DatabaseManager.isTransientTransactionConflict(DatabaseType.MYSQL,
                new SQLException("query timeout", "HYT00", 0)));
    }

    @Test
    void validatesTransactionOptions() {
        assertEquals(Connection.TRANSACTION_SERIALIZABLE,
                TransactionOptions.serializable(4).isolationLevel());
        assertEquals(4, TransactionOptions.serializable(4).maxAttempts());
        assertThrows(IllegalArgumentException.class, () -> TransactionOptions.serializable(0));
        assertThrows(IllegalArgumentException.class,
                () -> new TransactionOptions(Connection.TRANSACTION_NONE, 1));
    }

    private boolean incrementBelowLimit(
            DatabaseManager database,
            CyclicBarrier firstReads,
            AtomicInteger executions,
            Set<Connection> connections
    ) throws SQLException {
        AtomicBoolean firstExecution = new AtomicBoolean(true);
        return database.transaction(TransactionOptions.serializable(12), connection -> {
            executions.incrementAndGet();
            connections.add(connection);
            long current = database.queryOne(connection,
                    "SELECT `value` FROM counters WHERE `id` = ?",
                    row -> row.getLong("value"), 1).orElseThrow();
            if (firstExecution.compareAndSet(true, false)) await(firstReads);
            if (current >= 1L) return false;
            int updated = database.execute(connection,
                    "UPDATE counters SET `value` = ? WHERE `id` = ?", current + 1L, 1);
            if (updated != 1) throw new SQLException("Counter update failed");
            return true;
        });
    }

    private long counter(DatabaseManager database) throws SQLException {
        return database.queryOne("SELECT `value` FROM counters WHERE `id` = ?",
                row -> row.getLong("value"), 1).orElseThrow();
    }

    private DatabaseManager database(Path path) {
        return new DatabaseManager(DatabaseType.SQLITE, null, 0,
                null, null, null, path.toString());
    }

    private DatabaseManager trackingDatabase(ConnectionState state) {
        Connection connection = state.connection();
        return new DatabaseManager(DatabaseType.SQLITE, null, 0,
                null, null, null, tempDirectory.resolve("tracking.db").toString()) {
            @Override
            public Connection getConnection() {
                return connection;
            }
        };
    }

    private static void await(CyclicBarrier barrier) throws SQLException {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while coordinating transactions", exception);
        } catch (BrokenBarrierException | TimeoutException exception) {
            throw new SQLException("Unable to coordinate transactions", exception);
        }
    }

    private static final class ConnectionState {
        private final AtomicBoolean autoCommit = new AtomicBoolean(true);
        private final AtomicInteger isolation = new AtomicInteger(Connection.TRANSACTION_READ_COMMITTED);
        private final AtomicInteger commits = new AtomicInteger();
        private final AtomicInteger rollbacks = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();
        private SQLException closeFailure;

        private Connection connection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getAutoCommit" -> autoCommit.get();
                        case "setAutoCommit" -> {
                            autoCommit.set((boolean) arguments[0]);
                            yield null;
                        }
                        case "getTransactionIsolation" -> isolation.get();
                        case "setTransactionIsolation" -> {
                            isolation.set((int) arguments[0]);
                            yield null;
                        }
                        case "commit" -> {
                            commits.incrementAndGet();
                            yield null;
                        }
                        case "rollback" -> {
                            rollbacks.incrementAndGet();
                            yield null;
                        }
                        case "close" -> {
                            closes.incrementAndGet();
                            if (closeFailure != null) throw closeFailure;
                            yield null;
                        }
                        case "isClosed" -> false;
                        case "toString" -> "ConnectionState";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
