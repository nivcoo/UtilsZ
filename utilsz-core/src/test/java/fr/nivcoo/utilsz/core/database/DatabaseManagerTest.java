package fr.nivcoo.utilsz.core.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerTest {

    @TempDir
    Path tempDirectory;

    @Test
    void currentTimeMillisUsesDatabaseClockWithOwnedAndTransactionConnections() throws Exception {
        DatabaseManager database = new DatabaseManager(
                DatabaseType.SQLITE,
                null,
                0,
                null,
                null,
                null,
                tempDirectory.resolve("clock.db").toString()
        );

        try {
            long before = System.currentTimeMillis() - 1_000L;
            long ownedConnectionTime = database.currentTimeMillis();
            long transactionTime = database.transaction(database::currentTimeMillis);
            long after = System.currentTimeMillis() + 1_000L;

            assertTrue(ownedConnectionTime >= before && ownedConnectionTime <= after);
            assertTrue(transactionTime >= before && transactionTime <= after);
        } finally {
            database.closeConnection();
        }
    }

    @Test
    void currentTimeMillisQueriesMatchSupportedDialects() {
        assertTrue(DatabaseManager.currentTimeMillisQuery(DatabaseType.SQLITE).contains("julianday('now')"));
        assertEquals(
                "SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS SIGNED)",
                DatabaseManager.currentTimeMillisQuery(DatabaseType.MYSQL)
        );
        assertEquals(
                DatabaseManager.currentTimeMillisQuery(DatabaseType.MYSQL),
                DatabaseManager.currentTimeMillisQuery(DatabaseType.MARIADB)
        );
    }

    @Test
    void modelQueriesQuoteReservedIdentifiers() throws Exception {
        DatabaseManager database = new DatabaseManager(
                DatabaseType.SQLITE,
                null,
                0,
                null,
                null,
                null,
                tempDirectory.resolve("reserved-identifiers.db").toString()
        );

        try {
            ModelRepository<ReservedIdentifiers> repository = database.model(ReservedIdentifiers.MODEL);
            repository.createTable();
            repository.insert(new ReservedIdentifiers(0L, "initial", 1));

            ReservedIdentifiers inserted = repository.find()
                    .where("trigger", "initial")
                    .whereGreaterOrEqual("limit", 1)
                    .whereLessOrEqual("limit", 1)
                    .all()
                    .getFirst();
            assertEquals("initial", inserted.trigger());

            assertEquals(1, repository.update(inserted.select(), Map.of("trigger", "updated")));
            assertEquals("updated", repository.find().where("trigger", "updated").all().getFirst().trigger());
        } finally {
            database.closeConnection();
        }
    }

    @Test
    void insertReturningIdUsesGeneratedKeysWithOwnedAndTransactionConnections() throws Exception {
        DatabaseManager database = new DatabaseManager(
                DatabaseType.SQLITE,
                null,
                0,
                null,
                null,
                null,
                tempDirectory.resolve("generated-ids.db").toString()
        );

        try {
            ModelRepository<ReservedIdentifiers> repository = database.model(ReservedIdentifiers.MODEL);
            repository.createTable();

            long firstId = repository.insertReturningId(new ReservedIdentifiers(0L, "first", 1));
            long secondId = database.transaction(connection ->
                    repository.insertReturningId(connection, new ReservedIdentifiers(0L, "second", 2)));

            assertEquals(1L, firstId);
            assertEquals(2L, secondId);
            assertEquals("first", repository.find().where("select", firstId).all().getFirst().trigger());
            assertEquals("second", repository.find().where("select", secondId).all().getFirst().trigger());
        } finally {
            database.closeConnection();
        }
    }

    @Test
    void insertIfAbsentPreservesTheExistingRowAndOnlyIgnoresDuplicateKeys() throws Exception {
        DatabaseManager database = new DatabaseManager(
                DatabaseType.SQLITE,
                null,
                0,
                null,
                null,
                null,
                tempDirectory.resolve("insert-if-absent.db").toString()
        );

        try {
            ModelRepository<AtomicValues> repository = database.model(AtomicValues.MODEL);
            repository.createTable();

            AtomicValues initial = new AtomicValues("quest", 4L, 10L, 0L, "ACTIVE");
            assertTrue(repository.insertIfAbsent(initial));
            assertFalse(repository.insertIfAbsent(new AtomicValues("quest", 99L, 99L, 99L, "REPLACED")));
            assertEquals(initial, repository.find().where("key", "quest").all().getFirst());

            boolean transactionInsert = database.transaction(connection -> repository.insertIfAbsent(
                    connection, new AtomicValues("transaction", 1L, 2L, 0L, "ACTIVE")));
            boolean transactionDuplicate = database.transaction(connection -> repository.insertIfAbsent(
                    connection, new AtomicValues("transaction", 9L, 9L, 9L, "REPLACED")));
            assertTrue(transactionInsert);
            assertFalse(transactionDuplicate);

            assertThrows(SQLException.class, () -> repository.insertIfAbsent(
                    new AtomicValues("invalid", 0L, 0L, 0L, null)));
        } finally {
            database.closeConnection();
        }
    }

    @Test
    void atomicUpdatesComposeSetAddAndMonotonicMaxInsideTransactions() throws Exception {
        DatabaseManager database = new DatabaseManager(
                DatabaseType.SQLITE,
                null,
                0,
                null,
                null,
                null,
                tempDirectory.resolve("atomic-updates.db").toString()
        );

        try {
            ModelRepository<AtomicValues> repository = database.model(AtomicValues.MODEL);
            repository.createTable();
            repository.insert(new AtomicValues("quest", 4L, 10L, 0L, "PENDING"));

            assertEquals(1, repository.updateAtomic(Map.of(
                            "progress", AtomicUpdate.add(6L),
                            "target", AtomicUpdate.max(20L),
                            "revision", AtomicUpdate.add(1L),
                            "state", AtomicUpdate.set("ACTIVE")
                    ), "`key` = ? AND `revision` = ?", "quest", 0L));
            assertEquals(
                    new AtomicValues("quest", 10L, 20L, 1L, "ACTIVE"),
                    repository.find().where("key", "quest").all().getFirst()
            );

            int transactionUpdate = database.transaction(connection -> repository.updateAtomic(connection, Map.of(
                            "progress", AtomicUpdate.add(2L),
                            "target", AtomicUpdate.max(15L),
                            "revision", AtomicUpdate.add(1L),
                            "state", AtomicUpdate.set("READY")
                    ), "`key` = ? AND `revision` = ?", "quest", 1L));
            assertEquals(1, transactionUpdate);
            AtomicValues committed = new AtomicValues("quest", 12L, 20L, 2L, "READY");
            assertEquals(committed, repository.find().where("key", "quest").all().getFirst());

            assertEquals(0, repository.updateAtomic(
                    Map.of("progress", AtomicUpdate.add(100L)),
                    "`key` = ? AND `revision` = ?", "quest", 1L));
            assertThrows(SQLException.class, () -> database.transaction(connection -> {
                repository.updateAtomic(
                        connection,
                        Map.of("progress", AtomicUpdate.add(100L)),
                        "`key` = ?", "quest"
                );
                throw new SQLException("rollback");
            }));
            assertEquals(committed, repository.find().where("key", "quest").all().getFirst());

            assertThrows(IllegalArgumentException.class, () -> repository.updateAtomic(
                    Map.of("state", AtomicUpdate.add(1L)), "`key` = ?", "quest"));
        } finally {
            database.closeConnection();
        }
    }

    @Test
    void concurrentIndexCreationAcceptsAnIndexCreatedByAnotherInstance() throws Exception {
        RacingIndexManager database = new RacingIndexManager(
                tempDirectory.resolve("index-race.db"), true);
        try {
            database.createIndexIfAbsent("listings", "idx_state", java.util.List.of("state"));
            assertEquals(2, database.checks.get());
        } finally {
            database.closeConnection();
        }
    }

    @Test
    void indexCreationFailureIsPreservedWhenTheIndexStillDoesNotExist() {
        RacingIndexManager database = new RacingIndexManager(
                tempDirectory.resolve("index-failure.db"), false);
        try {
            assertThrows(SQLException.class, () -> database.createIndexIfAbsent(
                    "listings", "idx_state", java.util.List.of("state")));
        } finally {
            database.closeConnection();
        }
    }

    private record ReservedIdentifiers(long select, String trigger, int limit) {
        private static final DatabaseModel<ReservedIdentifiers> MODEL = new DatabaseModel<>() {
            @Override
            public ModelSchema<ReservedIdentifiers> schema() {
                return ModelSchema.<ReservedIdentifiers>of("order")
                        .id("select", ReservedIdentifiers::select)
                        .column("trigger", ColumnType.STRING, ReservedIdentifiers::trigger)
                        .column("limit", ColumnType.INT, ReservedIdentifiers::limit);
            }

            @Override
            public ReservedIdentifiers from(DatabaseRow row) {
                return new ReservedIdentifiers(
                        row.getLong("select"),
                        row.getString("trigger"),
                        row.getInt("limit")
                );
            }
        };
    }

    private record AtomicValues(String key, long progress, long target, long revision, String state) {
        private static final DatabaseModel<AtomicValues> MODEL = new DatabaseModel<>() {
            @Override
            public ModelSchema<AtomicValues> schema() {
                return ModelSchema.<AtomicValues>of("atomic_values")
                        .column("key", ColumnType.STRING, "PRIMARY KEY", AtomicValues::key)
                        .column("progress", ColumnType.LONG, AtomicValues::progress)
                        .column("target", ColumnType.LONG, AtomicValues::target)
                        .column("revision", ColumnType.LONG, AtomicValues::revision)
                        .column("state", ColumnType.STRING, AtomicValues::state);
            }

            @Override
            public AtomicValues from(DatabaseRow row) {
                return new AtomicValues(
                        row.getString("key"),
                        row.getLong("progress"),
                        row.getLong("target"),
                        row.getLong("revision"),
                        row.getString("state")
                );
            }
        };
    }

    private static final class RacingIndexManager extends DatabaseManager {
        private final AtomicInteger checks = new AtomicInteger();
        private final boolean appearsAfterFailure;

        private RacingIndexManager(Path path, boolean appearsAfterFailure) {
            super(DatabaseType.SQLITE, null, 0, null, null, null, path.toString());
            this.appearsAfterFailure = appearsAfterFailure;
        }

        @Override
        public boolean indexExists(String table, String index) {
            return checks.incrementAndGet() > 1 && appearsAfterFailure;
        }

        @Override
        public void executeUpdate(String query) throws SQLException {
            throw new SQLException("concurrent index creation");
        }
    }
}
