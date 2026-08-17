package fr.nivcoo.utilsz.core.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteProviderConcurrencyTest {

    @TempDir
    Path tempDirectory;

    @Test
    void connectionsAreIndependentAndTransientDatabasesRemainShared() throws Exception {
        DatabaseManager fileDatabase = database(tempDirectory.resolve("independent.db").toString());
        try {
            try (Connection first = fileDatabase.getConnection();
                 Connection second = fileDatabase.getConnection()) {
                assertNotSame(first, second);
                assertEquals(10_000, pragmaInt(first, "busy_timeout"));
                assertEquals("wal", pragmaString(second, "journal_mode"));
                first.close();
                assertFalse(second.isClosed());
            }
        } finally {
            fileDatabase.closeConnection();
        }
        assertFalse(fileDatabase.isConnected());
        assertThrows(SQLException.class, fileDatabase::getConnection);

        DatabaseManager memoryDatabase = database(":memory:");
        try {
            memoryDatabase.execute("CREATE TABLE shared_values (`id` INTEGER PRIMARY KEY, `value` BIGINT NOT NULL)");
            memoryDatabase.execute("INSERT INTO shared_values (`id`, `value`) VALUES (?, ?)", 1, 42L);

            try (Connection first = memoryDatabase.getConnection();
                 Connection second = memoryDatabase.getConnection()) {
                assertNotSame(first, second);
                first.close();
                assertEquals(42L, queryValue(second));
            }
        } finally {
            memoryDatabase.closeConnection();
        }
    }

    @Test
    void concurrentTransactionsCommitAtomicIncrementsAndRollbackFailures() throws Exception {
        DatabaseManager database = database(tempDirectory.resolve("concurrent.db").toString());
        ExecutorService executor = Executors.newFixedThreadPool(6);
        CountDownLatch ready = new CountDownLatch(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            database.execute("CREATE TABLE counters (`id` INTEGER PRIMARY KEY, `value` BIGINT NOT NULL)");
            database.execute("INSERT INTO counters (`id`, `value`) VALUES (?, ?)", 1, 0L);

            for (int worker = 0; worker < 6; worker++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent transaction start timed out.");
                    }

                    for (int iteration = 0; iteration < 20; iteration++) {
                        database.transaction(connection -> {
                            int affected = database.updateAtomic(
                                    connection,
                                    "counters",
                                    Map.of("value", AtomicUpdate.add(1L)),
                                    "`id` = ?",
                                    1
                            );
                            if (affected != 1) throw new SQLException("Atomic increment did not update the counter.");
                            return null;
                        });

                        try {
                            database.transaction(connection -> {
                                database.updateAtomic(
                                        connection,
                                        "counters",
                                        Map.of("value", AtomicUpdate.add(1_000L)),
                                        "`id` = ?",
                                        1
                                );
                                throw new SQLException("rollback");
                            });
                            throw new AssertionError("Rollback transaction unexpectedly committed.");
                        } catch (SQLException exception) {
                            if (!"rollback".equals(exception.getMessage())) throw exception;
                        }
                    }
                    return null;
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures) {
                future.get(90, TimeUnit.SECONDS);
            }

            long value = database.queryOne(
                    "SELECT `value` FROM counters WHERE `id` = ?",
                    row -> row.getLong("value"),
                    1
            ).orElseThrow();
            assertEquals(120L, value);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            database.closeConnection();
        }
    }

    private DatabaseManager database(String path) {
        return new DatabaseManager(DatabaseType.SQLITE, null, 0, null, null, null, path);
    }

    private int pragmaInt(Connection connection, String name) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA " + name)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private String pragmaString(Connection connection, String name) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA " + name)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private long queryValue(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT `value` FROM shared_values WHERE `id` = 1")) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }
}
